package com.readme.card;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

/**
 * GET /api/cards(거르기), GET /api/cards/{id}, GET /api/cards/tags. 모두 내 카드만 본다.
 */
@IntegrationTest
class CardReadTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	// ── 상세 ───────────────────────────────────────────────────────────

	@Test
	void detailHasEveryFieldAndTheFileNameOfItsSource() throws Exception {
		long me = this.fixtures.user("다온");
		long id = card(me, "배치 재실행 구조", List.of("Spring Batch"), List.of("문제해결"));
		long source = this.fixtures.cardSource(me, "FILE", "예시_이력서.docx", "본문");
		long job = this.fixtures.extractionJob(me, source);
		this.fixtures.extracted(id, source, job, "[\"결과의 30%는 자료 원문에 없어 [확인 필요: 30%]로 바꿨습니다.\"]");

		assertThat(this.mvc.get().uri("/api/cards/{id}", id).with(loginAs(me))).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{
				  "id": %d, "title": "배치 재실행 구조", "period": null, "org": null, "role": null,
				  "tags": ["Spring Batch"], "summary": "실패한 구간만 다시 돌리도록 나눈 경험", "usableFor": ["문제해결"],
				  "verified": false,
				  "star": { "situation": "상황", "task": "과제", "action": "행동", "result": "결과" },
				  "facts": ["처리 시간 2시간 → 10분"],
				  "visibility": "PRIVATE", "sourceId": %d, "sourceName": "예시_이력서.docx",
				  "extractionNotes": ["결과의 30%%는 자료 원문에 없어 [확인 필요: 30%%]로 바꿨습니다."],
				  "issues": []
				}
				""".formatted(id, source));
	}

	@Test
	void pastedTextHasNoSourceNameAndADeletedSourceLeavesNothing() throws Exception {
		long me = this.fixtures.user("다온");
		long pasted = card(me, "붙여넣은 글에서", List.of("Java"), List.of("성장"));
		long source = this.fixtures.cardSource(me, "TEXT", null, "붙여넣은 본문");
		this.fixtures.extracted(pasted, source, null, null);
		long direct = card(me, "직접 만든 카드", List.of("Java"), List.of("성장"));

		assertThat(this.mvc.get().uri("/api/cards/{id}", pasted).with(loginAs(me))).hasStatusOk()
			.bodyJson().isLenientlyEqualTo("{ \"sourceId\": %d, \"sourceName\": null, \"extractionNotes\": [] }".formatted(source));
		assertThat(this.mvc.get().uri("/api/cards/{id}", direct).with(loginAs(me))).hasStatusOk()
			.bodyJson().isLenientlyEqualTo("{ \"sourceId\": null, \"sourceName\": null }");
	}

	// ── 목록 ───────────────────────────────────────────────────────────

	@Test
	void listHasOnlyMyCardsLastChangedFirstInTheDetailShape() throws Exception {
		long me = this.fixtures.user("다온");
		long older = card(me, "먼저 만든 카드", List.of("Java"), List.of("성장"));
		long newer = card(me, "나중에 만든 카드", List.of("Java"), List.of("성장"));
		long earlier = card(me, "예전에 고친 카드", List.of("Java"), List.of("성장"));
		this.fixtures.touchCard(earlier); // 고친 때가 NOW 보다 앞이라 맨 뒤로 간다
		card(this.fixtures.user("가람"), "남의 카드", List.of("Java"), List.of("성장"));

		assertThat(ids(me, "")).containsExactly(newer, older, earlier);
		assertThat(this.mvc.get().uri("/api/cards").with(loginAs(me))).hasStatusOk().bodyJson()
			.isLenientlyEqualTo("""
					[
					  { "id": %d, "title": "나중에 만든 카드", "facts": ["처리 시간 2시간 → 10분"], "issues": [] },
					  { "id": %d }, { "id": %d }
					]
					""".formatted(newer, older, earlier));
	}

	@Test
	void filtersByTagTypeVerifiedJobAndText() throws Exception {
		long me = this.fixtures.user("다온");
		long order = card(me, "주문 API 개선", List.of("Spring Boot", "Redis"), List.of("직무역량"), "캐시 적중률 80%");
		this.mvc.post().uri("/api/cards/{id}/verify", order).with(loginAs(me)).with(xsrf()).exchange();
		long batch = card(me, "배치 재실행 구조", List.of("Spring Batch"), List.of("문제해결", "협업"));
		long source = this.fixtures.cardSource(me, "FILE", "예시_이력서.docx", "본문");
		long job = this.fixtures.extractionJob(me, source);
		this.fixtures.extracted(batch, source, job, null);
		long docs = card(me, "온보딩 문서 100% 정리", List.of("Notion"), List.of("협업"));
		// 남의 카드는 같은 태그여도 나오지 않는다
		card(this.fixtures.user("가람"), "남의 주문 API", List.of("Spring Boot"), List.of("직무역량"));

		// 태그: 다른 표기로 걸러도 대표 표기로 찾는다
		assertThat(ids(me, "tag=스프링부트")).containsExactly(order);
		assertThat(ids(me, "tag=Spring Batch")).containsExactly(batch);
		// 문항 유형: 다른 이름도 받는다
		assertThat(ids(me, "type=팀워크")).containsExactly(docs, batch);
		assertThat(ids(me, "verified=false")).containsExactly(docs, batch);
		assertThat(ids(me, "verified=true")).containsExactly(order);
		// 카드 확인 화면: 이 추출 작업에서 나온 확인 전 카드
		assertThat(ids(me, "verified=false&jobId=" + job)).containsExactly(batch);
		// 글자: 제목, 태그(대소문자 무관), 근거 숫자. % 는 글자 그대로다
		assertThat(ids(me, "q=재실행")).containsExactly(batch);
		assertThat(ids(me, "q=REDIS")).containsExactly(order);
		assertThat(ids(me, "q=적중률")).containsExactly(order);
		assertThat(ids(me, "q=%")).containsExactly(docs, order);
		// 여럿을 함께 쓰면 모두 맞는 것
		assertThat(ids(me, "type=협업&verified=false&q=배치")).containsExactly(batch);
		assertThat(ids(me, "tag=없는태그")).isEmpty();
	}

	@Test
	void unreadableFiltersAre400() {
		long me = this.fixtures.user("다온");

		assertThat(this.mvc.get().uri("/api/cards?type=리더십").with(loginAs(me))).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().isStrictlyEqualTo("""
					{ "code": "INVALID_INPUT", "message": "문항 유형은 지원동기 · 직무역량 · 협업 · 문제해결 · 성장 · 기타 중 하나로 넣어 주세요" }
					""");
		assertThat(this.mvc.get().uri("/api/cards?verified=maybe").with(loginAs(me))).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
		assertThat(this.mvc.get().uri("/api/cards?jobId=abc").with(loginAs(me))).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
	}

	@Test
	void noCardsIsAnEmptyListAndBeforeLoginIs401() {
		assertThat(this.mvc.get().uri("/api/cards").with(loginAs(this.fixtures.user("혼자")))).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("[]");
		assertThat(this.mvc.get().uri("/api/cards")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// ── 태그 ───────────────────────────────────────────────────────────

	@Test
	void tagsCountMyCardsMostUsedFirstThenByName() throws Exception {
		long me = this.fixtures.user("다온");
		card(me, "하나", List.of("Spring Boot", "Redis", "Java"), List.of("직무역량"));
		card(me, "둘", List.of("스프링부트", "redis"), List.of("직무역량"));
		card(this.fixtures.user("가람"), "남의 카드", List.of("Java", "Kotlin"), List.of("직무역량"));

		assertThat(this.mvc.get().uri("/api/cards/tags").with(loginAs(me))).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				[ { "name": "Redis", "count": 2 }, { "name": "Spring Boot", "count": 2 }, { "name": "Java", "count": 1 } ]
				""");
		assertThat(this.mvc.get().uri("/api/cards/tags").with(loginAs(this.fixtures.user("혼자")))).hasStatusOk()
			.bodyJson().isStrictlyEqualTo("[]");
	}

	/** 요약, STAR, 근거 숫자가 다 있어 태그·문항 유형 말고는 검사에 걸리지 않는 카드. 근거 숫자를 주지 않으면 "처리 시간 2시간 → 10분". */
	private long card(long userId, String title, List<String> tags, List<String> usableFor, String... facts)
			throws Exception {
		String body = """
				{
				  "title": "%s", "summary": "실패한 구간만 다시 돌리도록 나눈 경험", "tags": %s, "usableFor": %s,
				  "star": { "situation": "상황", "task": "과제", "action": "행동", "result": "결과" },
				  "facts": %s
				}
				""".formatted(title, json(tags), json(usableFor),
				json((facts.length > 0) ? List.of(facts) : List.of("처리 시간 2시간 → 10분")));
		String response = this.mvc.post().uri("/api/cards").with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(body).exchange().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private List<Long> ids(long userId, String query) throws Exception {
		String response = this.mvc.get().uri("/api/cards" + (query.isEmpty() ? "" : "?" + query)).with(loginAs(userId)).exchange()
			.getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(response, "$[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

	private static String json(List<String> values) {
		return values.stream().map((value) -> "\"" + value + "\"").toList().toString();
	}

}
