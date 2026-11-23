package com.readme.posting;

import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.readme.Fixtures;
import com.readme.FixedClockConfiguration;
import com.readme.IntegrationTest;
import com.readme.llm.FakeClaudeApi;
import com.readme.llm.LlmFeature;
import com.readme.posting.CardPriorities.Answer;
import com.readme.posting.CardPriorities.Pick;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

/** 내 공고의 경험카드 우선순위(카드 우선순위 설계): LLM 초안과 사용자가 정한 순서. Claude 는 FakeClaudeApi 가 답한다. */
@IntegrationTest
class CardPriorityTests {

	private static final String ANALYSIS = """
			{ "must": ["Java 기반 API 개발"], "nice": ["Redis 캐시 경험"], "duties": ["주문 API 운영"],
			  "keywords": ["Java", "Redis"], "questions": [] }
			""";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	FakeClaudeApi claude;

	@Autowired
	JdbcClient jdbc;

	long me;

	long posting;

	long api;

	long batch;

	long club;

	@BeforeEach
	void setUp() {
		this.claude.reset();
		this.me = this.fixtures.user("다온");
		this.posting = myPosting(this.me, ANALYSIS, "[]");
		this.api = card(this.me, "주문 API 개선", "Java", "Redis");
		this.batch = card(this.me, "알림 배치", "Docker");
		this.club = card(this.me, "동아리 회장", "리더십");
	}

	/** 초안은 LLM 이 고른 순서와 이유다. 내 카드가 아닌 id, 겹친 id 는 버린다. 상세에도 같은 목록이 보인다. */
	@Test
	void draftKeepsMyCardsInTheOrderTheLlmPicked() {
		long others = card(this.fixtures.user("가람"), "남의 카드", "Java");
		this.claude.willAnswer(new Answer(List.of(new Pick(this.api, "필수 요건의 Java API 개발에 답한다"),
				new Pick(others, "남의 카드"), new Pick(999_999_999L, "없는 카드"), new Pick(this.batch, "  주문 운영과 닿는다 "),
				new Pick(this.api, "겹친 카드"))));

		assertThat(draft(this.me, this.posting)).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "cards": [ { "cardId": %d, "title": "주문 API 개선", "experience": "", "reason": "필수 요건의 Java API 개발에 답한다" },
					             { "cardId": %d, "title": "알림 배치", "reason": "주문 운영과 닿는다" } ] }
					""".formatted(this.api, this.batch));
		assertThat(this.mvc.get().uri("/api/me/postings/{id}", this.posting).with(loginAs(this.me))).hasStatusOk()
			.bodyJson()
			.extractingPath("$.posting.cardPriority[*].cardId")
			.isEqualTo(List.of((int) this.api, (int) this.batch));
		assertThat(this.claude.lastFeature()).isEqualTo(LlmFeature.CARD_PRIORITY);
		// 공고 분석과 내 카드만 보낸다
		assertThat(this.claude.lastText()).contains("## 공고 — 예시랩 / 백엔드 개발자", "- Java 기반 API 개발",
				"### 카드 id " + this.club + ": 동아리 회장", "- 태그: Java, Redis")
			.doesNotContain("남의 카드");
		assertThat(spent(this.me)).isEqualTo(FakeClaudeApi.ANSWER_COST_MICROS);
	}

	/** 공고 분석이 없으면 공고 내용 묶음을 보낸다. 새 초안은 앞 목록을 바꾼다. */
	@Test
	void draftUsesTheSectionsWithoutAnAnalysisAndReplacesTheList() {
		long plain = myPosting(this.me, null, """
				[ { "title": "필수 요건", "items": ["Docker 배포 경험"] } ]
				""");
		this.claude.willAnswer(new Answer(List.of(new Pick(this.api, "앞 초안"))));
		assertThat(draft(this.me, plain)).hasStatusOk();
		this.claude.willAnswer(new Answer(List.of(new Pick(this.batch, "Docker 배포 경험에 답한다"))));

		assertThat(draft(this.me, plain)).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "cards": [ { "cardId": %d, "reason": "Docker 배포 경험에 답한다" } ] }
					""".formatted(this.batch));
		assertThat(this.claude.lastText()).contains("## 필수 요건\n- Docker 배포 경험");
	}

	/** 사용자가 정한 순서로 바꾼다. 앞 목록에 있던 카드는 이유가 남고, 더한 카드는 이유가 없다. 빈 목록이면 비운다. */
	@Test
	void reorderKeepsReasonsOfCardsAlreadyInTheList() {
		this.claude.willAnswer(new Answer(List.of(new Pick(this.api, "필수 요건에 답한다"), new Pick(this.batch, "업무에 닿는다"))));
		assertThat(draft(this.me, this.posting)).hasStatusOk();

		assertThat(reorder(this.me, this.posting, "[%d, %d]".formatted(this.club, this.api))).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "cards": [ { "cardId": %d, "title": "동아리 회장", "reason": null },
					             { "cardId": %d, "reason": "필수 요건에 답한다" } ] }
					""".formatted(this.club, this.api));
		assertThat(reorder(this.me, this.posting, "[]")).hasStatusOk().bodyJson().extractingPath("$.cards").isEqualTo(List.of());
		assertThat(this.claude.calls()).isEqualTo(1);
	}

	@Test
	void reorderChecksTheCards() {
		long others = card(this.fixtures.user("나래"), "남의 카드", "Java");
		long fourth = card(this.me, "넷째", "Git");
		long fifth = card(this.me, "다섯째", "SQL");
		long sixth = card(this.me, "여섯째", "AWS");

		assertThat(reorder(this.me, this.posting, "[%d, %d]".formatted(this.api, this.api))).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
		assertThat(reorder(this.me, this.posting, "[%d]".formatted(others))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(reorder(this.me, this.posting, "[%d, %d, %d, %d, %d, %d]".formatted(this.api, this.batch, this.club, fourth,
				fifth, sixth))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(this.mvc.put()
			.uri("/api/me/postings/{id}/card-priority", this.posting)
			.with(loginAs(this.me))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(this.jdbc.sql("select count(*) from user_posting_cards where posting_id = :id").param("id", this.posting)
			.query(Long.class).single()).isZero();
	}

	/** 내 공고에 없는 공고(남의 내 공고 포함)는 404 이고 LLM 을 부르지 않는다. */
	@Test
	void anotherPersonsPostingIs404() {
		long other = this.fixtures.user("마루");
		card(other, "마루 카드", "Java");

		assertThat(draft(other, this.posting)).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code")
			.isEqualTo("POSTING_NOT_FOUND");
		assertThat(reorder(other, this.posting, "[]")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.claude.calls()).isZero();
	}

	@Test
	void noCardsOrNoPostingContentIs409WithoutCallingTheLlm() {
		long empty = this.fixtures.user("라온");
		long hers = myPosting(empty, ANALYSIS, "[]");
		long bare = myPosting(this.me, null, "[]");

		assertThat(draft(empty, hers)).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("NO_CARDS");
		assertThat(draft(this.me, bare)).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
			.isEqualTo("NO_POSTING_CONTENT");
		assertThat(this.claude.calls()).isZero();
	}

	@Test
	void overTheLimitIs429AndTheListStays() {
		assertThat(reorder(this.me, this.posting, "[%d]".formatted(this.club))).hasStatusOk();
		this.jdbc.sql("""
				insert into llm_usage (user_id, feature, model, input_tokens, output_tokens, cost_micros, created_at)
				values (:userId, 'CARD_EXTRACT', 'claude-opus-5-5', 0, 0, 2000000, :at)
				""").param("userId", this.me).param("at", Timestamp.from(FixedClockConfiguration.NOW)).update();

		assertThat(draft(this.me, this.posting)).hasStatus(HttpStatus.TOO_MANY_REQUESTS).bodyJson().extractingPath("$.code")
			.isEqualTo("LLM_LIMIT_REACHED");
		assertThat(this.claude.calls()).isZero();
		assertThat(this.mvc.get().uri("/api/me/postings/{id}", this.posting).with(loginAs(this.me))).hasStatusOk()
			.bodyJson().extractingPath("$.posting.cardPriority[0].cardId").isEqualTo((int) this.club);
	}

	/** 쓸 수 없는 답은 502 이고 앞 목록은 그대로다. */
	@Test
	void llmFailureIs502AndTheListStays() {
		assertThat(reorder(this.me, this.posting, "[%d]".formatted(this.club))).hasStatusOk();
		this.claude.willRefuse();

		assertThat(draft(this.me, this.posting)).hasStatus(HttpStatus.BAD_GATEWAY);
		assertThat(this.jdbc.sql("select card_id from user_posting_cards where posting_id = :id").param("id", this.posting)
			.query(Long.class).list()).containsExactly(this.club);
	}

	/** 카드를 지우면 목록에서 빠지고, 내 공고에서 빼면 목록도 지워진다. */
	@Test
	void deletingACardOrRemovingThePostingDropsTheRows() {
		assertThat(reorder(this.me, this.posting, "[%d, %d]".formatted(this.api, this.batch))).hasStatusOk();

		assertThat(this.mvc.delete().uri("/api/cards/{id}", this.api).with(loginAs(this.me)).with(xsrf()))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.mvc.get().uri("/api/me/postings/{id}", this.posting).with(loginAs(this.me))).hasStatusOk()
			.bodyJson().extractingPath("$.posting.cardPriority[*].cardId").isEqualTo(List.of((int) this.batch));
		assertThat(this.mvc.delete().uri("/api/me/postings/{id}", this.posting).with(loginAs(this.me)).with(xsrf()))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.jdbc.sql("select count(*) from user_posting_cards where posting_id = :id").param("id", this.posting)
			.query(Long.class).single()).isZero();
	}

	private MvcTestResult draft(long userId, long postingId) {
		return this.mvc.post()
			.uri("/api/me/postings/{id}/card-priority/draft", postingId)
			.with(loginAs(userId))
			.with(xsrf())
			.exchange();
	}

	private MvcTestResult reorder(long userId, long postingId, String cardIds) {
		return this.mvc.put()
			.uri("/api/me/postings/{id}/card-priority", postingId)
			.with(loginAs(userId))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"cardIds\": " + cardIds + " }")
			.exchange();
	}

	/** 내 공고 하나. analysis 가 null 이면 분석이 없는 공고다. */
	private long myPosting(long userId, String analysis, String sections) {
		long postingId = this.fixtures.posting("예시랩", "백엔드 개발자", "UNKNOWN", null);
		this.fixtures.status(userId, postingId, "UNDECIDED", null, null);
		this.jdbc.sql("update postings set analysis = cast(:analysis as jsonb), sections = cast(:sections as jsonb) where id = :id")
			.param("id", postingId)
			.param("analysis", analysis)
			.param("sections", sections)
			.update();
		return postingId;
	}

	private long card(long userId, String title, String... tags) {
		String json = "{ \"title\": \"%s\", \"tags\": [%s] }".formatted(title,
				String.join(", ", java.util.Arrays.stream(tags).map((t) -> "\"" + t + "\"").toList()));
		assertThat(this.mvc.post()
			.uri("/api/cards")
			.with(loginAs(userId))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)).hasStatus(HttpStatus.CREATED);
		return this.jdbc.sql("select max(id) from experience_cards where user_id = :userId")
			.param("userId", userId)
			.query(Long.class)
			.single();
	}

	private long spent(long userId) {
		return this.jdbc.sql("select coalesce(sum(cost_micros), 0) from llm_usage where user_id = :userId")
			.param("userId", userId)
			.query(Long.class)
			.single();
	}

}
