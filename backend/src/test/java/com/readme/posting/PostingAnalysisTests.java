package com.readme.posting;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
import com.readme.llm.FakeClaudeApi;
import com.readme.llm.LlmFeature;
import com.readme.posting.PostingAnalysisRules.Answer;

/**
 * 공고 분석(설계 4.7): 그룹에 올리거나 내 공고에 넣으면 커밋 뒤 분석이 없는 공고를 한 번 분석하고, 두 상세가 함께 보여 준다.
 * Claude 는 FakeClaudeApi 가 답한다. 통합 테스트에서는 커밋 뒤의 일이 요청이 돌아오기 전에 끝난다(AwaitAsyncConfiguration).
 */
@IntegrationTest
class PostingAnalysisTests {

	private static final String SECTIONS = """
			[ { "title": "접수 방법", "items": ["문의 hr@example.com, 010-1234-5678"] },
			  { "title": "필수 요건", "items": ["Java 개발 경험 3년 이상", "스프링부트 운영 경험"] },
			  { "title": "자기소개서", "items": ["1. 지원 동기를 기술하시오 (800자)"] } ]
			""";

	private static final Answer ANSWER = new Answer(List.of("Java 개발 3년", "Spring Boot 운영"), List.of("Kubernetes"),
			List.of("주문 API 개발"), List.of("자바", "스프링부트", "Kafka 같은 것"),
			List.of(new Answer.Question("1. 지원 동기를 기술하시오", 800), new Answer.Question("입사 후 포부를 서술하시오", 0)));

	private static final String STORED = """
			{ "must": ["Java 개발 3년", "Spring Boot 운영"], "nice": ["Kubernetes"], "duties": ["주문 API 개발"],
			  "keywords": ["Java", "Spring Boot", "Kafka 같은 것"],
			  "questions": [{ "text": "1. 지원 동기를 기술하시오", "limit": 800 }] }
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

	long other;

	long group;

	@BeforeEach
	void setUp() {
		this.claude.reset();
		this.me = this.fixtures.user("다온");
		this.other = this.fixtures.user("나래");
		this.group = this.fixtures.group("백엔드 스터디", this.me, this.other);
	}

	@Test
	void uploadToAGroupAnalyzesTheJobAndTheDetailShowsIt() throws Exception {
		this.claude.willAnswer(ANSWER);

		MvcTestResult upload = upload(this.group, this.me, job(jobNumber(), SECTIONS)).exchange();

		assertThat(upload).hasStatus(HttpStatus.CREATED);
		long postId = idOf(upload, "$.post.id");
		assertThat(this.mvc.get().uri("/api/groups/{groupId}/posts/{postId}", this.group, postId).with(loginAs(this.me)))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.post.analysis")
			.isEqualTo(JsonPath.parse(STORED).json());
		assertThat(this.claude.calls()).isEqualTo(1);
		assertThat(this.claude.lastFeature()).isEqualTo(LlmFeature.POSTING_ANALYSIS);
		// 회사·공고명과 우선순위로 놓은 묶음을 보내고, 연락처는 가린다
		assertThat(this.claude.lastText()).contains("## 공고 — 예시랩 / 백엔드 개발자\n## 필수 요건\n- Java 개발 경험 3년 이상")
			.contains("- 문의 [이메일], [전화]")
			.doesNotContain("hr@example.com");
		assertThat(spent(this.me)).isEqualTo(FakeClaudeApi.ANSWER_COST_MICROS);
		assertThat(held(postId)).isFalse();
	}

	@Test
	void addToMyPostingsAnalyzesTheJob() throws Exception {
		this.claude.willAnswer(ANSWER);

		MvcTestResult add = add(this.me, job(jobNumber(), SECTIONS)).exchange();

		assertThat(add).hasStatus(HttpStatus.CREATED);
		long postingId = idOf(add, "$.posting.id");
		assertThat(this.mvc.get().uri("/api/me/postings/{postingId}", postingId).with(loginAs(this.me)))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.posting.analysis")
			.isEqualTo(JsonPath.parse(STORED).json());
		assertThat(spent(this.me)).isEqualTo(FakeClaudeApi.ANSWER_COST_MICROS);
	}

	/** 분석은 공고마다 한 번이다. 같은 공고를 다른 그룹에 올리거나 내 공고에 넣어도 다시 부르지 않고 같은 분석을 본다. */
	@Test
	void theSameJobIsAnalyzedOnceAndShared() throws Exception {
		long otherGroup = this.fixtures.group("공기업 전산직", this.other);
		String job = job(jobNumber(), SECTIONS);
		this.claude.willAnswer(ANSWER);
		long postId = idOf(upload(this.group, this.me, job).exchange(), "$.post.id");

		assertThat(upload(otherGroup, this.other, job)).hasStatus(HttpStatus.CREATED)
			.bodyJson().extractingPath("$.post.analysis.must").isEqualTo(List.of("Java 개발 3년", "Spring Boot 운영"));
		assertThat(add(this.other, job)).hasStatus(HttpStatus.CREATED)
			.bodyJson().extractingPath("$.posting.analysis.duties").isEqualTo(List.of("주문 API 개발"));
		assertThat(this.claude.calls()).isEqualTo(1);
		assertThat(spent(this.other)).isZero();
		assertThat(this.jdbc.sql("select count(*) from postings where id = :id and analysis is not null")
			.param("id", postId).query(Long.class).single()).isEqualTo(1);
	}

	/** 올린 사람이 한 달 한도에 닿았으면 돌리지 않는다. 다음에 다른 사람이 넣을 때 그 사람의 사용액으로 돈다. */
	@Test
	void overTheLimitSkipsAndTheNextPersonTriesAgain() throws Exception {
		this.jdbc.sql("""
				insert into llm_usage (user_id, feature, model, input_tokens, output_tokens, cost_micros, created_at)
				values (:userId, 'CARD_EXTRACT', 'claude-opus-5-5', 0, 0, 2000000, :at)
				""").param("userId", this.me).param("at", Timestamp.from(FixedClockConfiguration.NOW)).update();
		String job = job(jobNumber(), SECTIONS);
		this.claude.willAnswer(ANSWER);

		MvcTestResult upload = upload(this.group, this.me, job).exchange();

		assertThat(upload).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.post.analysis").isNull();
		assertThat(this.claude.calls()).isZero();
		assertThat(held(idOf(upload, "$.post.id"))).isFalse();
		assertThat(add(this.other, job)).hasStatus(HttpStatus.CREATED)
			.bodyJson().extractingPath("$.posting.analysis").isEqualTo(JsonPath.parse(STORED).json());
		assertThat(spent(this.other)).isEqualTo(FakeClaudeApi.ANSWER_COST_MICROS);
	}

	/** LLM 이 실패하면 분석 없이 올리기는 그대로 된다. 잡은 것을 놓아 다음에 다시 돈다. 되돌려진 올리기(409)는 분석하지 않는다. */
	@Test
	void llmFailureLeavesNoAnalysisAndTheUploadStands() throws Exception {
		String job = job(jobNumber(), SECTIONS);
		this.claude.willRefuse();

		MvcTestResult upload = upload(this.group, this.me, job).exchange();

		assertThat(upload).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.post.analysis").isNull();
		long postId = idOf(upload, "$.post.id");
		assertThat(spent(this.me)).isEqualTo(FakeClaudeApi.ANSWER_COST_MICROS);
		assertThat(held(postId)).isFalse();
		// 정해 둔 답이 없으면 키가 없는 서버와 같다(503 LLM_UNAVAILABLE). 사용액은 들지 않는다
		assertThat(add(this.other, job)).hasStatus(HttpStatus.CREATED)
			.bodyJson().extractingPath("$.posting.analysis").isNull();
		assertThat(spent(this.other)).isZero();
		assertThat(this.claude.calls()).isEqualTo(2);
		assertThat(upload(this.group, this.other, job)).hasStatus(HttpStatus.CONFLICT);
		assertThat(this.claude.calls()).isEqualTo(2);

		this.claude.willAnswer(ANSWER);
		assertThat(add(this.me, job)).hasStatus(HttpStatus.CREATED)
			.bodyJson().extractingPath("$.posting.analysis").isEqualTo(JsonPath.parse(STORED).json());
	}

	/** 필수 역량도 주요 업무도 없는 답은 쓸 수 없다(jobfit 의 검사). 저장하지 않는다. */
	@Test
	void anAnswerWithoutRequirementsOrDutiesIsNotStored() throws Exception {
		this.claude.willAnswer(new Answer(List.of(), List.of("우대"), List.of(), List.of("Java"), List.of()));

		assertThat(upload(this.group, this.me, job(jobNumber(), SECTIONS))).hasStatus(HttpStatus.CREATED)
			.bodyJson().extractingPath("$.post.analysis").isNull();
		assertThat(this.claude.calls()).isEqualTo(1);
	}

	/** 공고 내용 묶음이 없으면 보낼 글이 없어 부르지 않는다. */
	@Test
	void aJobWithoutSectionsIsNotAnalyzed() {
		this.claude.willAnswer(ANSWER);

		assertThat(upload(this.group, this.me, job(jobNumber(), "[]"))).hasStatus(HttpStatus.CREATED)
			.bodyJson().extractingPath("$.post.analysis").isNull();
		assertThat(this.claude.calls()).isZero();
	}

	/** 돌고 있는 분석이 있으면 다시 돌리지 않는다. 10분이 지나도 끝나지 않은 것은 멈춘 것으로 보고 다시 돌린다. */
	@Test
	void aRunningAnalysisIsNotStartedTwiceUnlessItIsStale() throws Exception {
		String job = job(jobNumber(), SECTIONS);
		long postId = idOf(upload(this.group, this.me, job).exchange(), "$.post.id");
		this.claude.reset();
		this.claude.willAnswer(ANSWER);
		setStartedAt(postId, Duration.ofMinutes(9));

		assertThat(add(this.me, job)).hasStatus(HttpStatus.CREATED)
			.bodyJson().extractingPath("$.posting.analysis").isNull();
		assertThat(this.claude.calls()).isZero();

		setStartedAt(postId, Duration.ofMinutes(11));
		assertThat(add(this.other, job)).hasStatus(HttpStatus.CREATED)
			.bodyJson().extractingPath("$.posting.analysis.must").isEqualTo(List.of("Java 개발 3년", "Spring Boot 운영"));
		assertThat(held(postId)).isFalse();
	}

	private void setStartedAt(long postingId, Duration ago) {
		this.jdbc.sql("update postings set analysis_started_at = :at where id = :id")
			.param("at", Timestamp.from(FixedClockConfiguration.NOW.minus(ago)))
			.param("id", postingId)
			.update();
	}

	/** 돌고 있는 분석이 잡아 둔 것이 있는지. 끝나면 놓는다. */
	private boolean held(long postingId) {
		return this.jdbc.sql("select analysis_started_at is not null from postings where id = :id")
			.param("id", postingId)
			.query(Boolean.class)
			.single();
	}

	private long spent(long userId) {
		return this.jdbc.sql("select coalesce(sum(cost_micros), 0) from llm_usage where user_id = :userId")
			.param("userId", userId)
			.query(Long.class)
			.single();
	}

	private MockMvcTester.MockMvcRequestBuilder upload(long groupId, long userId, String body) {
		return this.mvc.post().uri("/api/groups/{groupId}/posts", groupId).with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private MockMvcTester.MockMvcRequestBuilder add(long userId, String body) {
		return this.mvc.post().uri("/api/me/postings").with(loginAs(userId)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static String job(long number, String sections) {
		return """
				{ "company": "예시랩", "title": "백엔드 개발자", "deadline": { "kind": "always" },
				  "sourceUrl": "https://www.saramin.co.kr/zf_user/jobs/view?rec_idx=%d", "sections": %s }
				""".formatted(number, sections);
	}

	private static long jobNumber() {
		return ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
	}

	private static long idOf(MvcTestResult result, String path) throws Exception {
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue();
	}

}
