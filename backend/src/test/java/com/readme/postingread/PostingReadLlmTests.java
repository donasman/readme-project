package com.readme.postingread;

import java.sql.Timestamp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.readme.FixedClockConfiguration;
import com.readme.Fixtures;
import com.readme.IntegrationTest;
import com.readme.llm.FakeClaudeApi;
import com.readme.llm.LlmFeature;
import com.readme.postingread.PostingFieldsByLlm.FieldsAnswer;
import com.readme.postingread.PostingFieldsByLlm.FieldsAnswer.DeadlineAnswer;
import com.readme.postingread.PostingFieldsByLlm.FieldsAnswer.Field;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

/** 규칙으로 못 읽은 필수 칸을 LLM 으로 채우기. Claude 는 FakeClaudeApi 가 답한다. */
@IntegrationTest
class PostingReadLlmTests {

	/** 규칙으로는 회사명, 공고명, 마감을 읽지 못하는 글 */
	private static final String VAGUE = "함께 일할 분을 찾습니다\\n가나다소프트에서 데이터 엔지니어를 모집합니다.\\n지원은 회사 채용 홈페이지에서 받습니다.";

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
	void llmFillsOnlyTheUnreadFields() {
		long me = this.fixtures.user("다온");
		this.claude.willAnswer(new FieldsAnswer(new Field("가나다소프트", "가나다소프트에서 데이터 엔지니어를 모집합니다"),
				new Field("데이터 엔지니어", "데이터 엔지니어를 모집합니다"),
				new DeadlineAnswer("date", "2026-12-05", "12월 5일 마감"), new Field("", ""),
				new Field("회사 채용 홈페이지", "회사 채용 홈페이지")));

		MvcTestResult result = read(me, VAGUE);

		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "company": "가나다소프트", "title": "데이터 엔지니어", "deadline": { "kind": "date", "date": "2026-12-05" },
				  "deadlineName": "", "applyAt": "회사 채용 홈페이지",
				  "evidence": { "company": "가나다소프트에서 데이터 엔지니어를 모집합니다", "title": "데이터 엔지니어를 모집합니다",
				                "applyAt": "회사 채용 홈페이지" },
				  "unread": ["deadlineName", "sourceUrl"] }
				""");
		// 원문에 없는 근거("12월 5일 마감")는 싣지 않는다
		assertThat(result).bodyJson().doesNotHavePath("$.evidence.deadline");
		assertThat(this.claude.calls()).isEqualTo(1);
		assertThat(this.claude.lastFeature()).isEqualTo(LlmFeature.POSTING_FIELDS);
		assertThat(this.claude.lastText()).startsWith("오늘: 2026-11-13\n\n[공고 글]\n");
		assertThat(spent(me)).isEqualTo(FakeClaudeApi.ANSWER_COST_MICROS);
	}

	@Test
	void noLlmWhenTheRulesReadEveryRequiredField() {
		long me = this.fixtures.user("다온");

		assertThat(read(me, "[(주)예시랩] 백엔드 개발자(D-7)\\n마감일 : ~11/20(목) 23:59\\n주요 업무\\n- 주문 API 개발"))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "company": "예시랩", "title": "백엔드 개발자", "unread": ["deadlineName", "applyAt", "sourceUrl"] }
					""");
		assertThat(this.claude.calls()).isZero();
	}

	@Test
	void overTheMonthlyLimitTheRuleResultComesBackWithoutLlm() {
		long me = this.fixtures.user("다온");
		this.jdbc.sql("""
				insert into llm_usage (user_id, feature, model, input_tokens, output_tokens, cost_micros, created_at)
				values (:userId, 'CARD_EXTRACT', 'claude-opus-5-5', 0, 0, 2000000, :at)
				""").param("userId", me).param("at", Timestamp.from(FixedClockConfiguration.NOW)).update();

		assertThat(read(me, VAGUE)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "company": "", "title": "", "unread": ["company", "title", "deadline", "deadlineName", "applyAt", "sourceUrl"] }
				""");
		assertThat(this.claude.calls()).isZero();
	}

	@Test
	void unusableAnswerLeavesTheRuleResultButIsCharged() {
		long me = this.fixtures.user("다온");
		this.claude.willRefuse();

		assertThat(read(me, VAGUE)).hasStatusOk().bodyJson().extractingPath("$.company").isEqualTo("");
		assertThat(spent(me)).isEqualTo(FakeClaudeApi.ANSWER_COST_MICROS);
	}

	@Test
	void badDateAndTooLongValuesStayUnread() {
		long me = this.fixtures.user("다온");
		this.claude.willAnswer(new FieldsAnswer(new Field("가".repeat(201), ""), new Field("", ""),
				new DeadlineAnswer("date", "12월 5일", ""), new Field("", ""), new Field("", "")));

		assertThat(read(me, VAGUE)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "company": "", "deadline": { "kind": "unknown" },
				  "unread": ["company", "title", "deadline", "deadlineName", "applyAt", "sourceUrl"] }
				""");
	}

	private MvcTestResult read(long userId, String escapedText) {
		return this.mvc.post()
			.uri("/api/postings/read")
			.with(loginAs(userId))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"text\": \"" + escapedText + "\" }")
			.exchange();
	}

	private long spent(long userId) {
		return this.jdbc.sql("select coalesce(sum(cost_micros), 0) from llm_usage where user_id = :userId")
			.param("userId", userId)
			.query(Long.class)
			.single();
	}

}
