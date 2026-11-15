package com.readme.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.helpers.BetaMessageAccumulator;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaCacheCreation;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.BetaUsage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import com.anthropic.models.beta.messages.StructuredOutputConfig;
import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.core.JacksonException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.readme.common.error.ApiException;

/**
 * 공식 Java SDK(com.anthropic:anthropic-java)로 Claude 를 부른다. 정리 문서 4절 라이브러리 표의 정함(2026-11-16).
 *
 * <ul>
 * <li>구조화 출력: 답의 record 로 JSON 스키마를 만들어 output_config.format 으로 보낸다. effort 도 output_config 에 있다.</li>
 * <li>스트리밍으로 받는다. 출력이 길어도 HTTP 시간 제한에 걸리지 않는다. 최종 메시지는 SDK 의 누적기로 다시 만든다.</li>
 * <li>서버 쪽 fallback(beta, fallbacks: "default"): 안전 분류기가 거절하면 Anthropic 이 그 분류에 맞는 모델로 같은 요청을
 * 다시 돌린다. 그래서 beta 쪽 호출을 쓴다. 시도마다의 모델과 토큰은 usage.iterations 에 있다.</li>
 * <li>생각(thinking)은 보내지 않는다. Opus 5.5 는 늘 켜져 있고(끄면 400) 깊이는 effort 로 정한다. temperature 도 보내지 않는다.</li>
 * <li>다시 시도는 SDK 기본값이다(408·409·429·5xx 와 연결 오류를 2번).</li>
 * </ul>
 *
 * <p>기록(log)에 자료나 공고의 글을 남기지 않는다. 남기는 것은 기능, 상태 코드, 요청 id 뿐이다.
 */
final class AnthropicClaudeApi implements ClaudeApi, AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(AnthropicClaudeApi.class);

	/** API 키가 없으면 null 이다. */
	private final AnthropicClient client;

	AnthropicClaudeApi(AnthropicClient client) {
		this.client = client;
	}

	@Override
	public <T> ClaudeReply<T> send(ClaudeRequest<T> request) {
		if (this.client == null) {
			log.warn("Claude API 키(app.llm.api-key)가 없어 {} 를 부르지 않았다", request.feature());
			throw new ApiException(LlmErrorCode.LLM_UNAVAILABLE);
		}
		BetaMessage message;
		try (StreamResponse<BetaRawMessageStreamEvent> stream = this.client.beta()
			.messages()
			.createStreaming(params(request))) {
			BetaMessageAccumulator accumulator = BetaMessageAccumulator.create();
			stream.stream().forEach(accumulator::accumulate);
			message = accumulator.message();
		}
		catch (AnthropicServiceException ex) {
			// 키 오류(401), 계정 한도·크레딧 소진(400, 429), 과부하(529) 등. 다시 시도는 SDK 가 이미 했다.
			log.warn("Claude API 오류 {} {} ({}, 요청 {})", ex.statusCode(), ex.type().map(Object::toString).orElse(""),
					request.feature(), ex.requestId().orElse("-"));
			throw new ApiException(LlmErrorCode.LLM_UNAVAILABLE);
		}
		catch (AnthropicException ex) {
			log.warn("Claude API 를 부르지 못했다 ({}): {}", request.feature(), ex.getClass().getSimpleName());
			throw new ApiException(LlmErrorCode.LLM_UNAVAILABLE);
		}
		return reply(message, request.type());
	}

	static <T> StructuredMessageCreateParams<T> params(ClaudeRequest<T> request) {
		StructuredMessageCreateParams.Builder<T> builder = MessageCreateParams.builder()
			.outputConfig(StructuredOutputConfig.<T>builder()
				.effort(request.feature().effort())
				.format(request.type())
				.build())
			.model(request.model())
			.maxTokens(request.feature().maxTokens())
			.addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
			.fallbacksDefault()
			.addUserMessage(request.text());
		if (request.system() != null) {
			builder.system(request.system());
		}
		return builder.build();
	}

	/**
	 * 받은 메시지를 결과로 바꾼다. 거절이나 잘린 답이어도 토큰은 청구되므로 시도는 늘 담는다.
	 *
	 * <p>답은 글 블록을 모두 이어 읽는다. 답하는 중에 거절해 fallback 모델이 이어 쓰면, 글이 fallback 블록 앞뒤로 나뉘어 온다.
	 */
	static <T> ClaudeReply<T> reply(BetaMessage message, Class<T> type) {
		List<LlmAttempt> attempts = attempts(message);
		BetaStopReason stop = message.stopReason().orElse(null);
		if (BetaStopReason.REFUSAL.equals(stop)) {
			return ClaudeReply.failed(Outcome.REFUSED, attempts);
		}
		if (BetaStopReason.MAX_TOKENS.equals(stop) || BetaStopReason.MODEL_CONTEXT_WINDOW_EXCEEDED.equals(stop)) {
			return ClaudeReply.failed(Outcome.CUT_OFF, attempts);
		}
		if (!BetaStopReason.END_TURN.equals(stop)) {
			return ClaudeReply.failed(Outcome.UNREADABLE, attempts);
		}
		String text = message.content()
			.stream()
			.map(BetaContentBlock::text)
			.flatMap(Optional::stream)
			.map(BetaTextBlock::text)
			.collect(Collectors.joining());
		try {
			return ClaudeReply.done(ObjectMappers.jsonMapper().readValue(text, type), attempts);
		}
		catch (JacksonException ex) {
			return ClaudeReply.failed(Outcome.UNREADABLE, attempts);
		}
	}

	/** 시도마다의 모델과 토큰. usage.iterations 가 없으면 맨 위 usage 가 한 번의 시도다. */
	static List<LlmAttempt> attempts(BetaMessage message) {
		String served = message.model().asString();
		BetaUsage usage = message.usage();
		List<BetaUsage.Iteration> iterations = usage.iterations().orElse(List.of());
		if (iterations.isEmpty()) {
			long write5m = usage.cacheCreation()
				.map(BetaCacheCreation::ephemeral5mInputTokens)
				.orElse(usage.cacheCreationInputTokens().orElse(0L));
			long write1h = usage.cacheCreation().map(BetaCacheCreation::ephemeral1hInputTokens).orElse(0L);
			return List.of(new LlmAttempt(served, usage.inputTokens(), usage.outputTokens(), write5m, write1h,
					usage.cacheReadInputTokens().orElse(0L)));
		}
		List<LlmAttempt> attempts = new ArrayList<>();
		for (BetaUsage.Iteration iteration : iterations) {
			long write5m = iteration.cacheCreation()
				.map(BetaCacheCreation::ephemeral5mInputTokens)
				.orElse(iteration.cacheCreationInputTokens());
			long write1h = iteration.cacheCreation().map(BetaCacheCreation::ephemeral1hInputTokens).orElse(0L);
			attempts.add(new LlmAttempt(iteration.model().map(Model::asString).orElse(served), iteration.inputTokens(),
					iteration.outputTokens(), write5m, write1h, iteration.cacheReadInputTokens()));
		}
		return List.copyOf(attempts);
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.close();
		}
	}

}
