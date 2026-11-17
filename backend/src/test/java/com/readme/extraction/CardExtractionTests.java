package com.readme.extraction;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.readme.FixedClockConfiguration;
import com.readme.Fixtures;
import com.readme.IntegrationTest;
import com.readme.extraction.ExtractedCard.StarText;
import com.readme.extraction.ExtractionAnswer.Career;
import com.readme.extraction.ExtractionAnswer.Certificate;
import com.readme.extraction.ExtractionAnswer.ProfileAnswer;
import com.readme.llm.FakeClaudeApi;
import com.readme.llm.LlmFeature;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카드 뽑기(POST /api/card-sources/{id}/extract, GET /api/card-extractions/{jobId}). Claude 는 FakeClaudeApi 가 답하고, 작업은
 * 테스트 설정(app.extraction.synchronous)으로 요청 안에서 바로 돈다.
 */
@IntegrationTest
class CardExtractionTests {

	private static final String RESUME = """
			김다온 · [전화번호]

			주문 조회 API 개선 프로젝트 (2025-03 ~ 2025-05)
			- p95 응답 시간 1.8초 → 0.4초

			결제 알림 배치 개선 (2024-09 ~ 2024-12)
			- 하루 3만 건 알림을 Spring Batch로 옮김""";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	FakeClaudeApi claude;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void reset() {
		this.claude.reset();
	}

	@Test
	void extractsCardsAndFillsTheProfile() {
		long me = this.fixtures.user("다온");
		long source = this.fixtures.cardSource(me, "FILE", "이력서.pdf", RESUME);
		this.claude.willAnswer(new ExtractionAnswer(List.of(card("주문 조회 API 개선 프로젝트", "p95 응답 시간 1.8초 → 0.4초"),
				card("결제 알림 배치 개선", "처리 시간 70% 단축")),
				new ProfileAnswer("김다온", "1999-03-01", List.of(), List.of(new Certificate("컴활", "2022-08")),
						List.of(), List.of(new Career("가나다", "2024-03", "", "백엔드")))));

		MvcTestResult started = extract(me, source);

		assertThat(started).hasStatus(HttpStatus.ACCEPTED).bodyJson().isLenientlyEqualTo("""
				{ "sourceId": %d, "status": "done", "doneParts": 1, "totalParts": 1, "cardsSoFar": 2, "newCards": 2,
				  "skippedSameAs": [], "error": null }
				""".formatted(source));
		long jobId = jobId(started);
		assertThat(this.claude.lastFeature()).isEqualTo(LlmFeature.CARD_EXTRACT);
		assertThat(this.claude.lastText()).startsWith("## 이미 있는 카드\n(없음)\n\n## 태그 표기 (참고)\n")
			.contains("## 자료 — 이력서.pdf\n" + RESUME);

		assertThat(this.mvc.get().uri("/api/cards?jobId=" + jobId).with(loginAs(me))).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[ { "title": "결제 알림 배치 개선", "verified": false, "sourceId": %d, "sourceName": "이력서.pdf",
					    "sourceExcerpt": %s, "facts": [],
					    "extractionNotes": ["자료 원문에 없는 숫자가 있어 이 근거 숫자를 뺐습니다 — \\"처리 시간 70%% 단축\\""] },
					  { "title": "주문 조회 API 개선 프로젝트", "facts": ["p95 응답 시간 1.8초 → 0.4초"], "extractionNotes": [] } ]
					""".formatted(source, json(RESUME)));
		assertThat(this.mvc.get().uri("/api/me/profile").with(loginAs(me))).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "name": "김다온", "birthDate": "1999-03-01",
					  "certificates": [{ "name": "컴퓨터활용능력", "acquired": "2022-08" }],
					  "careers": [{ "company": "가나다", "start": "2024-03", "end": null, "role": "백엔드" }] }
					""");
		assertThat(this.mvc.get().uri("/api/card-sources").with(loginAs(me))).bodyJson()
			.isLenientlyEqualTo("""
					[ { "id": %d, "cardCount": 2, "latestJob": { "id": %d, "status": "done", "doneParts": 1, "totalParts": 1 } } ]
					""".formatted(source, jobId));
		assertThat(usage(me)).isEqualTo(FakeClaudeApi.ANSWER_COST_MICROS);
	}

	@Test
	void sameExperienceAsAnExistingCardIsSkipped() {
		long me = this.fixtures.user("다온");
		createCard(me, "주문 조회 API 개선 프로젝트");
		long source = this.fixtures.cardSource(me, "TEXT", null, RESUME);
		this.claude.willAnswer(new ExtractionAnswer(
				List.of(card("주문 조회 API 개선 (프로젝트)", ""), card("결제 알림 배치 개선", ""), card("결제 알림 배치 개선", "")),
				null));

		assertThat(extract(me, source)).bodyJson().isLenientlyEqualTo("""
				{ "status": "done", "cardsSoFar": 1, "skippedSameAs": ["주문 조회 API 개선 프로젝트", "결제 알림 배치 개선"] }
				""");
		assertThat(this.claude.lastText()).startsWith("## 이미 있는 카드\n- 주문 조회 API 개선 프로젝트\n")
			.contains("## 자료 — 붙여넣은 글\n");
	}

	@Test
	void longSourcesAreExtractedPartByPartAndCardsShowTheirPart() {
		long me = this.fixtures.user("다온");
		String first = "가".repeat(4990);
		String second = "두 번째 조각의 경험";
		long source = this.fixtures.cardSource(me, "TEXT", null, first + "\n" + "나".repeat(20) + "\n" + second);
		this.claude.willAnswer(new ExtractionAnswer(List.of(card("첫 조각 경험", "")), null));
		this.claude.willAnswer(new ExtractionAnswer(List.of(card("둘째 조각 경험", "")), null));

		MvcTestResult started = extract(me, source);

		assertThat(started).bodyJson().isLenientlyEqualTo("""
				{ "status": "done", "doneParts": 2, "totalParts": 2, "cardsSoFar": 2 }
				""");
		assertThat(this.claude.lastText()).contains("(조각 2/2)\n" + "나".repeat(20) + "\n" + second);
		assertThat(this.mvc.get().uri("/api/cards?jobId=" + jobId(started)).with(loginAs(me))).bodyJson()
			.extractingPath("$[?(@.title == '둘째 조각 경험')].sourceExcerpt")
			.asArray()
			.containsExactly("나".repeat(20) + "\n" + second);
	}

	@Test
	void failedJobResumesFromTheNextPartWhenStartedAgain() {
		long me = this.fixtures.user("다온");
		long source = this.fixtures.cardSource(me, "TEXT", null, "가".repeat(4990) + "\n" + "나".repeat(20) + "\n두 번째 조각");
		this.claude.willAnswer(new ExtractionAnswer(List.of(card("첫 조각 경험", "")), null));
		// 둘째 조각에서는 정해 둔 답이 없어 키가 없는 서버처럼 503 이다

		MvcTestResult failed = extract(me, source);

		assertThat(failed).hasStatus(HttpStatus.ACCEPTED).bodyJson().isLenientlyEqualTo("""
				{ "status": "failed", "doneParts": 1, "totalParts": 2, "cardsSoFar": 1, "newCards": 0,
				  "error": "지금은 AI 기능을 쓸 수 없습니다. 잠시 뒤 다시 시도해 주세요" }
				""");
		this.claude.willAnswer(new ExtractionAnswer(List.of(card("둘째 조각 경험", "")), null));

		MvcTestResult resumed = extract(me, source);

		assertThat(jobId(resumed)).isEqualTo(jobId(failed));
		assertThat(resumed).bodyJson().isLenientlyEqualTo("""
				{ "status": "done", "doneParts": 2, "cardsSoFar": 2, "newCards": 2, "error": null }
				""");
		assertThat(this.claude.calls()).isEqualTo(3);
	}

	@Test
	void unfinishedJobIsTheOneReturnedAndItRunsOn() {
		long me = this.fixtures.user("다온");
		long source = this.fixtures.cardSource(me, "TEXT", null, RESUME);
		long running = this.jdbc.sql("""
				insert into card_extraction_jobs (user_id, source_id, status, total_parts, done_parts)
				values (:userId, :sourceId, 'RUNNING', 1, 0) returning id
				""").param("userId", me).param("sourceId", source).query(Long.class).single();
		this.claude.willAnswer(new ExtractionAnswer(List.of(card("주문 조회 API 개선 프로젝트", "")), null));

		MvcTestResult result = extract(me, source);

		assertThat(jobId(result)).isEqualTo(running);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("done");
	}

	@Test
	void overTheMonthlyLimitNoJobStarts() {
		long me = this.fixtures.user("다온");
		long source = this.fixtures.cardSource(me, "TEXT", null, RESUME);
		this.jdbc.sql("""
				insert into llm_usage (user_id, feature, model, input_tokens, output_tokens, cost_micros, created_at)
				values (:userId, 'CARD_EXTRACT', 'claude-opus-5-5', 0, 0, 2000000, :at)
				""").param("userId", me).param("at", Timestamp.from(FixedClockConfiguration.NOW)).update();

		assertThat(extract(me, source)).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("LLM_LIMIT_REACHED");
		assertThat(this.claude.calls()).isZero();
	}

	@Test
	void othersSourcesAndJobsAre404() {
		long me = this.fixtures.user("다온");
		long other = this.fixtures.user("가람");
		long othersSource = this.fixtures.cardSource(other, "TEXT", null, RESUME);
		long othersJob = this.fixtures.extractionJob(other, othersSource);

		assertThat(extract(me, othersSource)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("SOURCE_NOT_FOUND");
		assertThat(this.mvc.get().uri("/api/card-extractions/" + othersJob).with(loginAs(me)))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("JOB_NOT_FOUND");
		assertThat(this.mvc.get().uri("/api/card-extractions/" + othersJob).with(loginAs(other))).hasStatusOk();
	}

	private static ExtractedCard card(String title, String fact) {
		return new ExtractedCard(title, "", "", "", List.of("Java"), title + " 요약", new StarText("", "", "", ""),
				fact.isEmpty() ? List.of() : List.of(fact), List.of("직무역량"));
	}

	private MvcTestResult extract(long userId, long sourceId) {
		return this.mvc.post()
			.uri("/api/card-sources/" + sourceId + "/extract")
			.with(loginAs(userId))
			.with(xsrf())
			.exchange();
	}

	private static long jobId(MvcTestResult result) {
		try {
			return ((Number) JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id")).longValue();
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private void createCard(long userId, String title) {
		assertThat(this.mvc.post()
			.uri("/api/cards")
			.with(loginAs(userId))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"title\": \"" + title + "\" }")).hasStatus(HttpStatus.CREATED);
	}

	private long usage(long userId) {
		return this.jdbc.sql("select coalesce(sum(cost_micros), 0) from llm_usage where user_id = :userId")
			.param("userId", userId)
			.query(Long.class)
			.single();
	}

	private static String json(String text) {
		return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}

}
