package com.readme.card;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

/**
 * POST /api/cards, PUT·DELETE /api/cards/{id}, POST /api/cards/{id}/verify.
 */
@IntegrationTest
class CardWriteTests {

	/** 태그는 다른 표기와 소문자, 사전에 없는 것, 겹친 것. 문항 유형은 다른 이름과 겹친 것. STAR 의 30% 는 근거 숫자에 없다. */
	private static final String ORDER_API = """
			{
			  "title": "  주문 조회 API 응답 시간 개선 ",
			  "period": "2025-03 ~ 2025-05",
			  "org": "예시 커머스 사내 프로젝트",
			  "role": "  ",
			  "summary": "N+1 조회를 제거하고 캐시를 붙여 응답 시간을 줄인 경험",
			  "tags": ["스프링부트", "java", "k8s", "사내 배포 도구", "Java"],
			  "usableFor": ["기술역량", "문제해결", "직무역량"],
			  "star": {
			    "situation": "p95 응답 시간이 1.8초였다.",
			    "task": "",
			    "action": "N+1을 fetch join으로 묶었다.",
			    "result": "p95 응답 시간이 0.4초로 줄었다. 문의도 30% 줄었다."
			  },
			  "facts": ["p95 응답 시간 1.8초 → 0.4초"]
			}
			""";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	// ── 만들기 ─────────────────────────────────────────────────────────

	@Test
	void createSavesCanonicalTagsAndTypesAndAnswersWithIssues() throws Exception {
		long me = this.fixtures.user("다온");

		MvcTestResult result = create(me, ORDER_API).exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
		long id = idOf(result);
		assertThat(result).bodyJson().isStrictlyEqualTo("""
				{
				  "id": %d,
				  "title": "주문 조회 API 응답 시간 개선",
				  "period": "2025-03 ~ 2025-05",
				  "org": "예시 커머스 사내 프로젝트",
				  "role": null,
				  "tags": ["Java", "Kubernetes", "Spring Boot", "사내 배포 도구"],
				  "summary": "N+1 조회를 제거하고 캐시를 붙여 응답 시간을 줄인 경험",
				  "usableFor": ["직무역량", "문제해결"],
				  "verified": false,
				  "star": {
				    "situation": "p95 응답 시간이 1.8초였다.",
				    "task": "",
				    "action": "N+1을 fetch join으로 묶었다.",
				    "result": "p95 응답 시간이 0.4초로 줄었다. 문의도 30%% 줄었다."
				  },
				  "facts": ["p95 응답 시간 1.8초 → 0.4초"],
				  "visibility": "PRIVATE",
				  "sourceId": null,
				  "sourceName": null,
				  "extractionNotes": [],
				  "issues": [
				    { "level": "경고", "field": "태그", "message": "태그 '사내 배포 도구'은(는) 키워드 사전에 없습니다" },
				    { "level": "경고", "field": "STAR", "message": "STAR에서 비어 있는 칸: 과제" },
				    { "level": "경고", "field": "STAR", "message": "STAR에 근거 숫자에 없는 숫자가 있습니다: 30%% — 카드를 확인하면 근거 숫자에 넣습니다" }
				  ]
				}
				""".formatted(id));
		// 태그는 대표 표기로, 사전에 없는 것은 친 그대로 저장한다
		assertThat(this.jdbc.sql("select tag from card_tags where card_id = :id order by tag").param("id", id)
			.query(String.class).list()).containsExactlyInAnyOrder("Java", "Kubernetes", "Spring Boot", "사내 배포 도구");
		assertThat(this.jdbc.sql("select question_type from card_usable_for where card_id = :id").param("id", id)
			.query(String.class).list()).containsExactlyInAnyOrder("직무역량", "문제해결");
		// 빈 STAR 칸은 null 로 둔다
		assertThat(this.jdbc.sql("select task is null from experience_cards where id = :id").param("id", id)
			.query(Boolean.class).single()).isTrue();
	}

	@Test
	void onlyTheTitleIsRequiredAndTheCheckReportsTheRest() throws Exception {
		MvcTestResult result = create(this.fixtures.user("다온"), """
				{ "title": "제목만" }
				""").exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().isLenientlyEqualTo("""
				{
				  "title": "제목만", "period": null, "org": null, "role": null, "summary": "", "tags": [], "usableFor": [],
				  "star": { "situation": "", "task": "", "action": "", "result": "" }, "facts": [],
				  "issues": [
				    { "level": "오류", "field": "태그", "message": "태그 칸이 비어 있습니다" },
				    { "level": "오류", "field": "요약", "message": "요약 칸이 비어 있습니다" },
				    { "level": "오류", "field": "쓸 문항 유형", "message": "쓸 문항 유형 칸이 비어 있습니다" },
				    { "level": "경고", "field": "근거 숫자", "message": "근거 숫자가 없습니다 — 이 카드로 쓴 초안에는 수치를 쓸 수 없습니다" },
				    { "level": "경고", "field": "STAR", "message": "STAR에서 비어 있는 칸: 상황, 과제, 행동, 결과" }
				  ]
				}
				""");
	}

	@Test
	void lengthLimitsAreTheColumnLengths() {
		long me = this.fixtures.user("다온");

		assertInvalid(create(me, "{ \"title\": \"   \" }"));
		assertInvalid(create(me, "{ \"title\": \"%s\" }".formatted("가".repeat(201))));
		assertInvalid(create(me, "{ \"title\": \"제목\", \"period\": \"%s\" }".formatted("1".repeat(51))));
		assertInvalid(create(me, "{ \"title\": \"제목\", \"org\": \"%s\" }".formatted("가".repeat(201))));
		assertInvalid(create(me, "{ \"title\": \"제목\", \"role\": \"%s\" }".formatted("가".repeat(201))));
		assertInvalid(create(me, "{ \"title\": \"제목\", \"summary\": \"%s\" }".formatted("가".repeat(201))));
		assertInvalid(create(me, "{ \"title\": \"제목\", \"tags\": [\"%s\"] }".formatted("가".repeat(101))));
		assertInvalid(create(me, "{ \"title\": \"제목\", \"tags\": [\" \"] }"));
		assertInvalid(create(me, "{ \"title\": \"제목\", \"facts\": [\"%s\"] }".formatted("가".repeat(501))));
		assertInvalid(create(me, "{ \"title\": \"제목\", \"facts\": [\"\"] }"));
		assertThat(create(me, """
				{ "title": "%s", "period": "%s", "org": "%s", "role": "%s", "summary": "%s", "tags": ["%s"], "facts": ["%s"] }
				""".formatted("가".repeat(200), "1".repeat(50), "가".repeat(200), "가".repeat(200), "가".repeat(200),
				"가".repeat(100), "가".repeat(500)))).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void usableForMustBeOneOfTheSixTypesAfterReadingAliases() {
		long me = this.fixtures.user("다온");

		assertThat(create(me, "{ \"title\": \"제목\", \"usableFor\": [\"직무역량\", \"리더십\"] }"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().isStrictlyEqualTo("""
					{ "code": "INVALID_INPUT", "message": "쓸 문항 유형은 지원동기 · 직무역량 · 협업 · 문제해결 · 성장 · 기타 중에서 골라 주세요" }
					""");
		assertThat(create(me, "{ \"title\": \"제목\", \"usableFor\": [\" 팀워크 \", \"입사후 포부\"] }"))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson().extractingPath("$.usableFor").isEqualTo(List.of("지원동기", "협업"));
	}

	@Test
	void beforeLoginIs401() {
		assertThat(this.mvc.post().uri("/api/cards").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{ \"title\": \"제목\" }")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// ── 고치기, 지우기 ───────────────────────────────────────────────────

	@Test
	void updateReplacesEveryFieldAndKeepsVerified() throws Exception {
		long me = this.fixtures.user("다온");
		long id = idOf(create(me, ORDER_API).exchange());
		verify(me, id).exchange();

		assertThat(update(me, id, """
				{
				  "title": "주문 API 개선", "period": "2025 ~ 현재", "org": null, "role": "백엔드",
				  "summary": "캐시", "tags": ["Redis"], "usableFor": ["성장"],
				  "star": { "situation": "s", "task": "t", "action": "a", "result": "r" },
				  "facts": ["쿼리 21회 → 1회", "p95 1.8초 → 0.4초"]
				}
				""")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{
				  "id": %d, "title": "주문 API 개선", "period": "2025 ~ 현재", "org": null, "role": "백엔드",
				  "summary": "캐시", "tags": ["Redis"], "usableFor": ["성장"], "verified": true,
				  "star": { "situation": "s", "task": "t", "action": "a", "result": "r" },
				  "facts": ["쿼리 21회 → 1회", "p95 1.8초 → 0.4초"], "issues": []
				}
				""".formatted(id));
		// 근거 숫자는 보낸 순서대로 seq 1부터
		assertThat(this.jdbc.sql("select seq || ':' || body from card_facts where card_id = :id order by seq").param("id", id)
			.query(String.class).list()).containsExactly("1:쿼리 21회 → 1회", "2:p95 1.8초 → 0.4초");
		assertThat(this.jdbc.sql("select tag from card_tags where card_id = :id").param("id", id).query(String.class).list())
			.containsExactly("Redis");
	}

	@Test
	void deleteRemovesTheCardWithItsRows() throws Exception {
		long me = this.fixtures.user("다온");
		long id = idOf(create(me, ORDER_API).exchange());

		assertThat(this.mvc.delete().uri("/api/cards/{id}", id).with(loginAs(me)).with(xsrf()))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.mvc.get().uri("/api/cards/{id}", id).with(loginAs(me))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.jdbc.sql("""
				select (select count(*) from card_tags where card_id = :id) + (select count(*) from card_facts where card_id = :id)
				     + (select count(*) from card_usable_for where card_id = :id)
				""").param("id", id).query(Long.class).single()).isZero();
	}

	@Test
	void someoneElsesCardIs404LikeAMissingOne() throws Exception {
		long owner = this.fixtures.user("가람");
		long me = this.fixtures.user("다온");
		long id = idOf(create(owner, ORDER_API).exchange());

		assertNotFound(this.mvc.get().uri("/api/cards/{id}", id).with(loginAs(me)));
		assertNotFound(update(me, id, "{ \"title\": \"가로채기\" }"));
		assertNotFound(this.mvc.delete().uri("/api/cards/{id}", id).with(loginAs(me)).with(xsrf()));
		assertNotFound(verify(me, id));
		assertNotFound(this.mvc.get().uri("/api/cards/{id}", Long.MAX_VALUE).with(loginAs(me)));
		// 주인의 카드는 그대로다
		assertThat(this.mvc.get().uri("/api/cards/{id}", id).with(loginAs(owner))).hasStatusOk()
			.bodyJson().isLenientlyEqualTo("{ \"title\": \"주문 조회 API 응답 시간 개선\", \"verified\": false }");
	}

	// ── 확인 ───────────────────────────────────────────────────────────

	@Test
	void verifyMovesStarOnlyNumbersIntoFactsAndClearsNotes() throws Exception {
		long me = this.fixtures.user("다온");
		long id = idOf(create(me, ORDER_API).exchange());
		long source = this.fixtures.cardSource(me, "FILE", "예시_포트폴리오.pdf", "본문");
		this.fixtures.extracted(id, source, this.fixtures.extractionJob(me, source),
				"[\"결과의 30%는 자료 원문에 없어 [확인 필요: 30%]로 바꿨습니다.\"]");

		assertThat(verify(me, id)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{
				  "id": %d, "verified": true,
				  "facts": ["p95 응답 시간 1.8초 → 0.4초", "문의도 30%% 줄었다."],
				  "sourceId": %d, "sourceName": "예시_포트폴리오.pdf", "extractionNotes": [],
				  "issues": [
				    { "level": "경고", "field": "태그", "message": "태그 '사내 배포 도구'은(는) 키워드 사전에 없습니다" },
				    { "level": "경고", "field": "STAR", "message": "STAR에서 비어 있는 칸: 과제" }
				  ]
				}
				""".formatted(id, source));
		assertThat(this.jdbc.sql("select verified_at from experience_cards where id = :id").param("id", id)
			.query(OffsetDateTime.class).single().toInstant()).isEqualTo(NOW);
		assertThat(this.jdbc.sql("select extraction_notes is null from experience_cards where id = :id").param("id", id)
			.query(Boolean.class).single()).isTrue();
	}

	@Test
	void verifyingTwiceAddsNothingMore() throws Exception {
		long me = this.fixtures.user("다온");
		long id = idOf(create(me, ORDER_API).exchange());
		verify(me, id).exchange();

		assertThat(verify(me, id)).hasStatusOk().bodyJson().extractingPath("$.facts")
			.isEqualTo(List.of("p95 응답 시간 1.8초 → 0.4초", "문의도 30% 줄었다."));
	}

	private MockMvcTester.MockMvcRequestBuilder create(long userId, String json) {
		return this.mvc.post().uri("/api/cards").with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(json);
	}

	private MockMvcTester.MockMvcRequestBuilder update(long userId, long id, String json) {
		return this.mvc.put().uri("/api/cards/{id}", id).with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(json);
	}

	private MockMvcTester.MockMvcRequestBuilder verify(long userId, long id) {
		return this.mvc.post().uri("/api/cards/{id}/verify", id).with(loginAs(userId)).with(xsrf());
	}

	private static long idOf(MvcTestResult result) throws Exception {
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}

	private static void assertInvalid(MockMvcTester.MockMvcRequestBuilder request) {
		assertThat(request).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
	}

	private static void assertNotFound(MockMvcTester.MockMvcRequestBuilder request) {
		assertThat(request).hasStatus(HttpStatus.NOT_FOUND).bodyJson().isStrictlyEqualTo("""
				{ "code": "CARD_NOT_FOUND", "message": "카드를 찾을 수 없습니다" }
				""");
	}

}
