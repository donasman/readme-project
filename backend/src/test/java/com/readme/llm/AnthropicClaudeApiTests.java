package com.readme.llm;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.readme.common.error.ApiException;
import com.readme.llm.ClaudeApi.ClaudeReply;
import com.readme.llm.ClaudeApi.ClaudeRequest;
import com.readme.llm.ClaudeApi.LlmAttempt;
import com.readme.llm.ClaudeApi.Outcome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * 실제 SDK 로 내 PC 의 가짜 Claude API(127.0.0.1)를 부른다. 보내는 요청의 모양과 스트림으로 받은 답을 읽는 것을 본다.
 * 실제 API 는 부르지 않는다. 답의 모양은 Messages API 의 스트리밍 이벤트를 줄인 것이다.
 */
class AnthropicClaudeApiTests {

	record Draft(String title, List<String> tags) {
	}

	private static final ClaudeRequest<Draft> REQUEST = new ClaudeRequest<>("claude-opus-5-5", LlmFeature.CARD_EXTRACT,
			"카드를 뽑는다", "이력서 본문", Draft.class);

	private HttpServer server;

	private final AtomicReference<String> body = new AtomicReference<>();

	private final AtomicReference<Headers> headers = new AtomicReference<>();

	private AnthropicClaudeApi api;

	@BeforeEach
	void start() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		this.server.start();
		this.api = new AnthropicClaudeApi(AnthropicOkHttpClient.builder()
			.apiKey("sk-ant-api03-test")
			.baseUrl("http://127.0.0.1:" + this.server.getAddress().getPort())
			.maxRetries(0)
			.build());
	}

	@AfterEach
	void stop() {
		this.api.close();
		this.server.stop(0);
	}

	@Test
	void sendsStructuredStreamingRequestWithFallback() throws IOException {
		answer(200, stream("end_turn", 1200, 300, "{\\\"title\\\":\\\"주문 시스템\\\",", "\\\"tags\\\":[\\\"Java\\\"]}"));

		ClaudeReply<Draft> reply = this.api.send(REQUEST);

		assertThat(reply.outcome()).isEqualTo(Outcome.DONE);
		assertThat(reply.value()).isEqualTo(new Draft("주문 시스템", List.of("Java")));
		assertThat(reply.attempts()).containsExactly(new LlmAttempt("claude-opus-5-5", 1200, 300, 100, 0, 50));

		JsonNode sent = ObjectMappers.jsonMapper().readTree(this.body.get());
		assertThat(sent.get("model").asText()).isEqualTo("claude-opus-5-5");
		assertThat(sent.get("max_tokens").asLong()).isEqualTo(32_000);
		assertThat(sent.get("stream").asBoolean()).isTrue();
		assertThat(sent.get("fallbacks").asText()).isEqualTo("default");
		assertThat(sent.get("system").asText()).isEqualTo("카드를 뽑는다");
		assertThat(sent.at("/messages/0/content").asText()).isEqualTo("이력서 본문");
		assertThat(sent.at("/output_config/effort").asText()).isEqualTo("medium");
		assertThat(sent.at("/output_config/format/type").asText()).isEqualTo("json_schema");
		assertThat(sent.at("/output_config/format/schema/required").toString()).contains("title", "tags");
		assertThat(sent.has("thinking")).isFalse();
		assertThat(sent.has("temperature")).isFalse();
		// SDK 3.x 는 키를 x-api-key 가 아니라 Authorization 머리로 보낸다
		assertThat(this.headers.get().getFirst("authorization")).isEqualTo("Bearer sk-ant-api03-test");
		assertThat(this.headers.get().getFirst("anthropic-beta")).contains("server-side-fallback-2026-07-01");
	}

	@Test
	void refusalKeepsTheTokens() {
		answer(200, stream("refusal", 900, 0));

		ClaudeReply<Draft> reply = this.api.send(REQUEST);

		assertThat(reply.outcome()).isEqualTo(Outcome.REFUSED);
		assertThat(reply.value()).isNull();
		assertThat(reply.attempts()).containsExactly(new LlmAttempt("claude-opus-5-5", 900, 0, 100, 0, 50));
	}

	@Test
	void cutOffAnswer() {
		answer(200, stream("max_tokens", 900, 32_000, "{\\\"title\\\":\\\"주문"));

		assertThat(this.api.send(REQUEST).outcome()).isEqualTo(Outcome.CUT_OFF);
	}

	@Test
	void answerThatIsNotTheShape() {
		answer(200, stream("end_turn", 900, 10, "카드가 없습니다"));

		assertThat(this.api.send(REQUEST).outcome()).isEqualTo(Outcome.UNREADABLE);
	}

	@Test
	void apiErrorIsUnavailable() {
		answer(529, """
				{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}
				""");

		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> this.api.send(REQUEST))
			.satisfies((ex) -> assertThat(ex.errorCode()).isEqualTo(LlmErrorCode.LLM_UNAVAILABLE));
	}

	@Test
	void noKeyIsUnavailable() {
		AnthropicClaudeApi noKey = new AnthropicClaudeApi(null);

		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> noKey.send(REQUEST))
			.satisfies((ex) -> assertThat(ex.errorCode()).isEqualTo(LlmErrorCode.LLM_UNAVAILABLE));
	}

	@Test
	void fallbackAttemptsAreSeparate() throws IOException {
		// 서버 쪽 fallback: Opus 5.5 가 답하기 전에 거절하고 Opus 4.8 이 답했다. 맨 위 usage 는 마지막 시도만 담는다.
		BetaMessage message = ObjectMappers.jsonMapper().readValue("""
				{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-4-8",
				 "content":[{"type":"fallback","from":{"model":"claude-opus-5-5"},"to":{"model":"claude-opus-4-8"}},
				            {"type":"text","text":"{\\"title\\":\\"a\\",\\"tags\\":[]}"}],
				 "stop_reason":"end_turn","stop_sequence":null,"stop_details":null,"container":null,"context_management":null,
				 "usage":{"input_tokens":1000,"output_tokens":400,"cache_creation_input_tokens":0,"cache_read_input_tokens":0,
				   "iterations":[
				     {"type":"message","model":"claude-opus-5-5","input_tokens":1000,"output_tokens":0,
				      "cache_creation_input_tokens":0,"cache_read_input_tokens":0},
				     {"type":"fallback_message","model":"claude-opus-4-8","input_tokens":1000,"output_tokens":400,
				      "cache_creation_input_tokens":0,"cache_read_input_tokens":0}]}}
				""", BetaMessage.class);

		ClaudeReply<Draft> reply = AnthropicClaudeApi.reply(message, Draft.class);

		assertThat(reply.outcome()).isEqualTo(Outcome.DONE);
		assertThat(reply.value()).isEqualTo(new Draft("a", List.of()));
		assertThat(reply.attempts()).containsExactly(new LlmAttempt("claude-opus-5-5", 1000, 0, 0, 0, 0),
				new LlmAttempt("claude-opus-4-8", 1000, 400, 0, 0, 0));
	}

	/** 다음 요청에 이 상태와 본문으로 답한다. 받은 요청의 본문과 머리를 기억한다. */
	private void answer(int status, String responseBody) {
		this.server.createContext("/", (exchange) -> {
			this.body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			this.headers.set(new Headers(exchange.getRequestHeaders()));
			respond(exchange, status, responseBody);
		});
	}

	private static void respond(HttpExchange exchange, int status, String responseBody) {
		byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", (status == 200) ? "text/event-stream" : "application/json");
		try (OutputStream out = exchange.getResponseBody()) {
			exchange.sendResponseHeaders(status, bytes.length);
			out.write(bytes);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		finally {
			exchange.close();
		}
	}

	/**
	 * 스트리밍 응답. texts 는 글 조각(JSON 문자열 안에 들어갈 꼴로 이스케이프한 것)이다. 입력의 캐시 쓰기 100, 캐시 읽기 50 이다.
	 */
	private static String stream(String stopReason, int inputTokens, int outputTokens, String... texts) {
		StringBuilder sse = new StringBuilder();
		event(sse, "message_start", """
				{"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
				 "content":[],"stop_reason":null,"stop_sequence":null,"stop_details":null,"container":null,"context_management":null,
				 "usage":{"input_tokens":%d,"output_tokens":1,"cache_creation_input_tokens":100,"cache_read_input_tokens":50,
				          "cache_creation":{"ephemeral_5m_input_tokens":100,"ephemeral_1h_input_tokens":0}}}}
				""".formatted(inputTokens));
		if (texts.length > 0) {
			event(sse, "content_block_start", """
					{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}
					""");
			for (String text : texts) {
				event(sse, "content_block_delta", """
						{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"%s"}}
						""".formatted(text));
			}
			event(sse, "content_block_stop", """
					{"type":"content_block_stop","index":0}
					""");
		}
		event(sse, "message_delta", """
				{"type":"message_delta","delta":{"stop_reason":"%s","stop_sequence":null},"usage":{"output_tokens":%d}}
				""".formatted(stopReason, outputTokens));
		event(sse, "message_stop", """
				{"type":"message_stop"}
				""");
		return sse.toString();
	}

	private static void event(StringBuilder sse, String name, String json) {
		sse.append("event: ").append(name).append('\n');
		sse.append("data: ").append(json.replace("\n", "")).append("\n\n");
	}

}
