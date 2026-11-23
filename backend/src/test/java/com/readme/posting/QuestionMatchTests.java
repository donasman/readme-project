package com.readme.posting;

import java.nio.charset.StandardCharsets;
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
import com.readme.IntegrationTest;
import com.readme.llm.FakeClaudeApi;
import com.readme.llm.LlmFeature;
import com.readme.posting.PostingQuestions.Answer;
import com.readme.posting.PostingQuestions.Match;
import com.readme.posting.PostingQuestions.Pick;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

/** 내 공고의 자기소개서 문항과 문항마다 고른 카드(문항 매칭 설계). Claude 는 FakeClaudeApi 가 답한다. */
@IntegrationTest
class QuestionMatchTests {

	private static final String ANALYSIS = """
			{ "must": ["Java 기반 API 개발"], "nice": [], "duties": ["주문 API 운영"], "keywords": ["Java"],
			  "questions": [ { "text": "지원 직무와 관련한 경험을 쓰세요", "limit": 800 },
			                 { "text": "가장 어려웠던 실패 경험을 쓰세요", "limit": 300 } ] }
			""";

	private static final String NO_QUESTIONS = """
			{ "must": ["Java"], "nice": [], "duties": [], "keywords": [], "questions": [] }
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
		this.posting = myPosting(this.me, ANALYSIS);
		this.api = card(this.me, "주문 API 개선");
		this.batch = card(this.me, "알림 배치");
		this.club = card(this.me, "동아리 회장");
	}

	/** 저장 전에는 공고 분석의 문항이 보인다(id 없음, 카드 없음). */
	@Test
	void detailShowsTheAnalysisQuestionsBeforeSaving() {
		assertThat(this.mvc.get().uri("/api/me/postings/{id}", this.posting).with(loginAs(this.me))).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "posting": { "questions": [
					  { "id": null, "text": "지원 직무와 관련한 경험을 쓰세요", "limit": 800, "type": null, "cards": [] },
					  { "id": null, "text": "가장 어려웠던 실패 경험을 쓰세요", "limit": 300, "type": null, "cards": [] } ] } }
					""");
		assertThat(questionIds(this.posting)).isEmpty();
	}

	/** 고르기는 저장 전이면 공고 분석의 문항을 먼저 저장하고 그 id 로 LLM 에 보낸다. LLM 이 실패해도 문항은 저장된 채로 남는다. */
	@Test
	void matchSavesTheAnalysisQuestionsFirst() {
		this.claude.willRefuse();

		assertThat(match(this.me, this.posting)).hasStatus(HttpStatus.BAD_GATEWAY);
		List<Long> ids = questionIds(this.posting);
		assertThat(ids).hasSize(2);
		assertThat(this.claude.lastFeature()).isEqualTo(LlmFeature.QUESTION_MATCH);
		assertThat(this.claude.lastText()).contains("## 공고 — 예시랩 / 백엔드 개발자", "- Java 기반 API 개발",
				"### 문항 id " + ids.get(0) + " (글자 수 800자)\n지원 직무와 관련한 경험을 쓰세요",
				"### 문항 id " + ids.get(1) + " (글자 수 300자)", "### 카드 id " + this.club + ": 동아리 회장");
		assertThat(this.mvc.get().uri("/api/me/postings/{id}", this.posting).with(loginAs(this.me))).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "posting": { "questions": [
					  { "id": %d, "text": "지원 직무와 관련한 경험을 쓰세요", "limit": 800, "type": null, "cards": [] },
					  { "id": %d, "text": "가장 어려웠던 실패 경험을 쓰세요", "limit": 300, "type": null, "cards": [] } ] } }
					""".formatted(ids.get(0), ids.get(1)));
	}

	/** LLM 의 답을 걸러 저장한다: 남의 카드, 겹친 카드, 수를 넘는 카드, 아홉 밖의 유형, 여섯 밖의 칸을 버린다. 다시 고르면 통째로 바꾼다. */
	@Test
	void matchKeepsTheCheckedAnswer() {
		long others = card(this.fixtures.user("가람"), "남의 카드");
		List<Long> ids = saveQuestions(this.me, this.posting, """
				[ { "text": "직무 경험", "limit": 800 }, { "text": "실패 경험", "limit": 300 }, { "text": "입사 후 포부" } ]
				""");
		this.claude.willAnswer(new Answer(List.of(
				new Match(ids.get(0), "JOB", List.of(new Pick(others, "남의 카드", List.of("action")),
						new Pick(this.api, "필수 요건의 Java API 개발에 답한다", List.of("action", "result", "feeling")),
						new Pick(this.api, "겹친 카드", List.of()), new Pick(this.batch, "운영과 닿는다", List.of("task")),
						new Pick(this.club, "셋째", List.of()))),
				new Match(ids.get(1), "SOMETHING", List.of(new Pick(this.club, "회장 때 실패", List.of("obstacle", "learning")),
						new Pick(this.batch, "둘째", List.of()))),
				new Match(ids.get(2), "FUTURE", List.of()))));

		assertThat(match(this.me, this.posting)).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "questions": [
					  { "id": %d, "type": "JOB", "cards": [
					    { "cardId": %d, "title": "주문 API 개선", "experience": "", "reason": "필수 요건의 Java API 개발에 답한다",
					      "emphasis": ["action", "result"] },
					    { "cardId": %d, "reason": "운영과 닿는다", "emphasis": ["task"] } ] },
					  { "id": %d, "type": null, "cards": [
					    { "cardId": %d, "reason": "회장 때 실패", "emphasis": ["obstacle", "learning"] } ] },
					  { "id": %d, "type": "FUTURE", "cards": [] } ] }
					""".formatted(ids.get(0), this.api, this.batch, ids.get(1), this.club, ids.get(2)));
		questions(this.me, this.posting).extractingPath("$.posting.questions[0].cards[*].cardId")
			.isEqualTo(List.of((int) this.api, (int) this.batch));
		assertThat(this.claude.lastText()).doesNotContain("남의 카드").contains("(글자 수 없음)");

		// 입력이 같으면 저장한 답을 다시 쓰므로 문항 하나를 고쳐 다시 고르게 한다
		saveQuestions(this.me, this.posting, """
				[ { "id": %d, "text": "직무 경험", "limit": 800 }, { "id": %d, "text": "실패 경험", "limit": 300 },
				  { "id": %d, "text": "입사 후 포부와 계획" } ]
				""".formatted(ids.get(0), ids.get(1), ids.get(2)));
		this.claude.willAnswer(new Answer(List.of(new Match(ids.get(0), "PROBLEM", List.of(new Pick(this.club, "다시", null))))));
		assertThat(match(this.me, this.posting)).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "questions": [ { "type": "PROBLEM", "cards": [ { "cardId": %d, "emphasis": [] } ] },
					                 { "type": null, "cards": [] }, { "type": null, "cards": [] } ] }
					""".formatted(this.club));
	}

	/**
	 * 입력(지시문, 공고, 문항, 카드)이 같으면 LLM 을 부르지 않고 저장한 답을 다시 쓴다. 처음 누를 때 저장한 문항의 id 로 보낸 입력이
	 * key 라서 두 번째 누름이 맞는다. 사용액도 들지 않는다.
	 */
	@Test
	void sameInputReusesTheStoredAnswer() throws Exception {
		this.claude.whileAnswering(() -> {
			List<Long> ids = questionIds(this.posting);
			this.claude.willAnswer(new Answer(List.of(new Match(ids.get(0), "JOB", List.of(new Pick(this.api, "직무에 답한다", List.of("action")))),
					new Match(ids.get(1), "FAILURE", List.of(new Pick(this.club, "실패 장면", List.of("learning")))))));
		});
		MvcTestResult firstResult = match(this.me, this.posting);
		assertThat(firstResult).hasStatusOk();
		String first = firstResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(first).contains("\"reused\":false", "\"type\":\"JOB\"");

		assertThat(match(this.me, this.posting)).hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo(first.replace("\"reused\":false", "\"reused\":true"));
		assertThat(this.claude.calls()).isEqualTo(1);
		assertThat(spent(this.me)).isEqualTo(FakeClaudeApi.ANSWER_COST_MICROS);
	}

	/** 사용자가 문항의 카드를 바꾼 뒤에도 입력이 같으면 고르기는 저장한 LLM 결과로 되돌린다. */
	@Test
	void matchAfterAManualEditRestoresTheStoredResult() {
		long question = saveQuestions(this.me, this.posting, "[ { \"text\": \"직무 경험\" } ]").getFirst();
		this.claude.willAnswer(new Answer(List.of(new Match(question, "JOB", List.of(new Pick(this.api, "직무에 답한다", List.of("action")))))));
		assertThat(match(this.me, this.posting)).hasStatusOk();
		assertThat(setCards(this.me, this.posting, question, "[%d]".formatted(this.club))).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{ \"reused\": false }");

		assertThat(match(this.me, this.posting)).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "questions": [ { "id": %d, "type": "JOB", "cards": [ { "cardId": %d, "reason": "직무에 답한다" } ] } ],
					  "reused": true }
					""".formatted(question, this.api));
		assertThat(this.claude.calls()).isEqualTo(1);
	}

	/** 문항을 고치면 입력이 바뀌므로 LLM 을 다시 부른다. */
	@Test
	void editingAQuestionCallsTheLlmAgain() {
		long question = saveQuestions(this.me, this.posting, "[ { \"text\": \"직무 경험\" } ]").getFirst();
		this.claude.willAnswer(new Answer(List.of(new Match(question, "JOB", List.of()))));
		assertThat(match(this.me, this.posting)).hasStatusOk();
		assertThat(putQuestions(this.me, this.posting, "[ { \"id\": %d, \"text\": \"직무 경험\", \"limit\": 300 } ]".formatted(question)))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{ \"reused\": false }");
		this.claude.willAnswer(new Answer(List.of(new Match(question, "PROBLEM", List.of()))));

		assertThat(match(this.me, this.posting)).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "questions": [ { "id": %d, "type": "PROBLEM" } ], "reused": false }
					""".formatted(question));
		assertThat(this.claude.calls()).isEqualTo(2);
		assertThat(this.claude.lastText()).contains("(글자 수 300자)");
	}

	/** 다시 쓰기는 사용액이 들지 않으므로 한도에 닿아도 된다. 카드를 고쳐 입력이 바뀌면 429 다. */
	@Test
	void reuseWorksOverTheLimit() {
		long question = saveQuestions(this.me, this.posting, "[ { \"text\": \"직무 경험\" } ]").getFirst();
		this.claude.willAnswer(new Answer(List.of(new Match(question, "JOB", List.of(new Pick(this.api, "직무에 답한다", List.of()))))));
		assertThat(match(this.me, this.posting)).hasStatusOk();
		this.fixtures.useUpLlmBudget(this.me);

		assertThat(match(this.me, this.posting)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "questions": [ { "type": "JOB", "cards": [ { "cardId": %d } ] } ], "reused": true }
				""".formatted(this.api));
		this.fixtures.retitleCard(this.batch, "알림 배치 개선");
		assertThat(match(this.me, this.posting)).hasStatus(HttpStatus.TOO_MANY_REQUESTS).bodyJson().extractingPath("$.code")
			.isEqualTo("LLM_LIMIT_REACHED");
		assertThat(this.claude.calls()).isEqualTo(1);
	}

	/** 어느 문항에도 유형이나 카드가 남지 않은 답(지어낸 문항 id)은 두지 않으므로, 같은 입력으로 다시 누르면 LLM 에 다시 묻는다. */
	@Test
	void anAnswerWithNothingUsableIsNotReused() {
		long question = saveQuestions(this.me, this.posting, "[ { \"text\": \"직무 경험\" } ]").getFirst();
		this.claude.willAnswer(new Answer(List.of(new Match(1, "JOB", List.of(new Pick(this.api, "번호를 지어냈다", List.of()))),
				new Match(question, "NOT_A_TYPE", List.of(new Pick(999_999_999L, "없는 카드", List.of()))))));
		assertThat(match(this.me, this.posting)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "questions": [ { "id": %d, "type": null, "cards": [] } ], "reused": false }
				""".formatted(question));
		this.claude.willAnswer(new Answer(List.of(new Match(question, "JOB", List.of()))));

		assertThat(match(this.me, this.posting)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "questions": [ { "id": %d, "type": "JOB" } ], "reused": false }
				""".formatted(question));
		assertThat(this.claude.calls()).isEqualTo(2);
	}

	/** 고친 때만 바뀐 카드는 입력을 바꾸지 않는다. 문항을 같은 글로 다시 두어도(id 를 보내면) 입력이 같다. */
	@Test
	void unchangedContentStillReuses() {
		long question = saveQuestions(this.me, this.posting, "[ { \"text\": \"직무 경험\", \"limit\": 800 } ]").getFirst();
		this.claude.willAnswer(new Answer(List.of(new Match(question, "JOB", List.of(new Pick(this.api, "직무에 답한다", List.of()))))));
		assertThat(match(this.me, this.posting)).hasStatusOk();
		this.fixtures.touchCard(this.api);
		saveQuestions(this.me, this.posting, "[ { \"id\": %d, \"text\": \"직무 경험\", \"limit\": 800 } ]".formatted(question));

		assertThat(match(this.me, this.posting)).hasStatusOk().bodyJson().isLenientlyEqualTo("{ \"reused\": true }");
		assertThat(this.claude.calls()).isEqualTo(1);
	}

	/** LLM 이 고르는 동안 지워진 문항의 답은 버리고, 남은 문항만 저장한다. */
	@Test
	void matchDropsAnswersForQuestionsDeletedMeanwhile() {
		List<Long> ids = saveQuestions(this.me, this.posting, """
				[ { "text": "직무 경험", "limit": 800 }, { "text": "실패 경험", "limit": 800 } ]
				""");
		this.claude.whileAnswering(() -> this.jdbc.sql("delete from user_posting_questions where id = :id")
			.param("id", ids.get(1))
			.update());
		this.claude.willAnswer(new Answer(List.of(new Match(ids.get(0), "JOB", List.of(new Pick(this.api, "직무", List.of()))),
				new Match(ids.get(1), "FAILURE", List.of(new Pick(this.club, "실패", List.of()))))));

		assertThat(match(this.me, this.posting)).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "questions": [ { "id": %d, "type": "JOB", "cards": [ { "cardId": %d } ] } ] }
					""".formatted(ids.get(0), this.api));
		assertThat(this.jdbc.sql("select count(*) from user_posting_question_cards where question_id = :id")
			.param("id", ids.get(1))
			.query(Long.class)
			.single()).isZero();
	}

	/** LLM 이 고르는 동안 지워진 카드는 빼고 남은 카드만 빈자리 없이 저장한다. */
	@Test
	void matchSkipsCardsDeletedMeanwhile() {
		long question = saveQuestions(this.me, this.posting, "[ { \"text\": \"직무 경험\", \"limit\": 800 } ]").getFirst();
		this.claude.whileAnswering(() -> this.jdbc.sql("delete from experience_cards where id = :id")
			.param("id", this.api)
			.update());
		this.claude.willAnswer(new Answer(List.of(new Match(question, "JOB",
				List.of(new Pick(this.api, "지워질 카드", List.of()), new Pick(this.batch, "남는 카드", List.of("action")))))));

		assertThat(match(this.me, this.posting)).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "questions": [ { "id": %d, "type": "JOB", "cards": [ { "cardId": %d, "reason": "남는 카드" } ] } ] }
					""".formatted(question, this.batch));
		assertThat(this.jdbc.sql("select position from user_posting_question_cards where question_id = :id")
			.param("id", question).query(Integer.class).list()).containsExactly(0);
	}

	/** 문항 목록 두기: id 를 보낸 문항은 카드와 유형이 남고, 빠진 문항은 지워지고, 새 문항이 들어간다. 빈 목록이면 모두 지운다. */
	@Test
	void replaceKeepsCardsOfQuestionsSentWithTheirId() {
		List<Long> ids = saveQuestions(this.me, this.posting, """
				[ { "text": "직무 경험", "limit": 800 }, { "text": "실패 경험", "limit": 800 } ]
				""");
		this.claude.willAnswer(new Answer(List.of(new Match(ids.get(0), "JOB", List.of(new Pick(this.api, "직무", List.of()))),
				new Match(ids.get(1), "FAILURE", List.of(new Pick(this.club, "실패", List.of()))))));
		assertThat(match(this.me, this.posting)).hasStatusOk();

		assertThat(putQuestions(this.me, this.posting, """
				[ { "text": "  새 문항  ", "limit": null }, { "id": %d, "text": "고친 직무 경험", "limit": 1000 } ]
				""".formatted(ids.get(0)))).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "questions": [ { "text": "새 문항", "limit": null, "type": null, "cards": [] },
					                 { "id": %d, "text": "고친 직무 경험", "limit": 1000, "type": "JOB",
					                   "cards": [ { "cardId": %d, "reason": "직무" } ] } ] }
					""".formatted(ids.get(0), this.api));
		questions(this.me, this.posting).extractingPath("$.posting.questions[*].text")
			.isEqualTo(List.of("새 문항", "고친 직무 경험"));
		assertThat(questionIds(this.posting)).hasSize(2).doesNotContain(ids.get(1));

		assertThat(putQuestions(this.me, this.posting, "[]")).hasStatusOk().bodyJson().extractingPath("$.questions")
			.isEqualTo(List.of());
		// 모두 뺀 뒤에는 공고 분석의 문항으로 돌아가지 않는다
		assertThat(this.mvc.get().uri("/api/me/postings/{id}", this.posting).with(loginAs(this.me))).hasStatusOk()
			.bodyJson().extractingPath("$.posting.questions").isEqualTo(List.of());
		assertThat(match(this.me, this.posting)).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
			.isEqualTo("NO_QUESTIONS");
	}

	@Test
	void replaceChecksTheQuestions() {
		long otherPosting = myPosting(this.me, ANALYSIS);
		long foreign = saveQuestions(this.me, otherPosting, "[ { \"text\": \"다른 공고의 문항\" } ]").getFirst();
		long mine = saveQuestions(this.me, this.posting, "[ { \"text\": \"내 문항\" } ]").getFirst();
		String eleven = String.join(", ", java.util.Collections.nCopies(11, "{ \"text\": \"문항\" }"));

		for (String body : List.of("[" + eleven + "]", "[ { \"text\": \"  \" } ]", "[ { \"text\": \"%s\" } ]".formatted("가".repeat(1001)),
				"[ { \"text\": \"문항\", \"limit\": 0 } ]", "[ { \"text\": \"문항\", \"limit\": 10001 } ]",
				"[ { \"id\": %d, \"text\": \"남의 공고 문항\" } ]".formatted(foreign),
				"[ { \"id\": %d, \"text\": \"하나\" }, { \"id\": %d, \"text\": \"둘\" } ]".formatted(mine, mine))) {
			assertThat(putQuestions(this.me, this.posting, body)).hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
		}
		assertThat(this.mvc.put()
			.uri("/api/me/postings/{id}/questions", this.posting)
			.with(loginAs(this.me))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(questionIds(this.posting)).containsExactly(mine);
		assertThat(putQuestions(this.me, this.posting, "[ { \"text\": \"%s\", \"limit\": 10000 } ]".formatted("가".repeat(1000))))
			.hasStatusOk();
	}

	/** 카드 바꾸기: 앞에 있던 카드는 이유와 살릴 칸이 남고, 더한 카드는 없다. 빈 목록이면 비운다. */
	@Test
	void setCardsKeepsReasonsOfCardsAlreadyThere() {
		long question = saveQuestions(this.me, this.posting, "[ { \"text\": \"실패 경험\", \"limit\": 800 } ]").getFirst();
		this.claude.willAnswer(new Answer(List.of(new Match(question, "FAILURE",
				List.of(new Pick(this.club, "회장 때 실패", List.of("obstacle", "learning")))))));
		assertThat(match(this.me, this.posting)).hasStatusOk();

		assertThat(setCards(this.me, this.posting, question, "[%d, %d]".formatted(this.batch, this.club))).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "questions": [ { "id": %d, "type": "FAILURE", "cards": [
					  { "cardId": %d, "title": "알림 배치", "reason": null, "emphasis": [] },
					  { "cardId": %d, "reason": "회장 때 실패", "emphasis": ["obstacle", "learning"] } ] } ] }
					""".formatted(question, this.batch, this.club));
		questions(this.me, this.posting).extractingPath("$.posting.questions[0].cards[*].cardId")
			.isEqualTo(List.of((int) this.batch, (int) this.club));
		assertThat(setCards(this.me, this.posting, question, "[]")).hasStatusOk().bodyJson()
			.extractingPath("$.questions[0].cards").isEqualTo(List.of());
		assertThat(this.claude.calls()).isEqualTo(1);
	}

	@Test
	void setCardsChecksTheCards() {
		List<Long> ids = saveQuestions(this.me, this.posting, """
				[ { "text": "긴 문항", "limit": 800 }, { "text": "짧은 문항", "limit": 499 } ]
				""");
		long others = card(this.fixtures.user("나래"), "남의 카드");
		long otherPosting = myPosting(this.me, ANALYSIS);
		long elsewhere = saveQuestions(this.me, otherPosting, "[ { \"text\": \"다른 공고의 문항\" } ]").getFirst();

		for (String cardIds : List.of("[%d, %d, %d]".formatted(this.api, this.batch, this.club),
				"[%d, %d]".formatted(this.api, this.api), "[%d]".formatted(others))) {
			assertThat(setCards(this.me, this.posting, ids.get(0), cardIds)).hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
		}
		assertThat(setCards(this.me, this.posting, ids.get(1), "[%d, %d]".formatted(this.api, this.batch)))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(setCards(this.me, this.posting, ids.get(1), "[%d]".formatted(this.api))).hasStatusOk();
		assertThat(setCards(this.me, this.posting, elsewhere, "[]")).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("QUESTION_NOT_FOUND");
	}

	/** 남의 내 공고는 모든 문항 API 가 404 이고 LLM 을 부르지 않는다. */
	@Test
	void anotherPersonsPostingIs404() {
		long other = this.fixtures.user("마루");
		card(other, "마루 카드");
		long question = saveQuestions(this.me, this.posting, "[ { \"text\": \"내 문항\" } ]").getFirst();

		assertThat(match(other, this.posting)).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code")
			.isEqualTo("POSTING_NOT_FOUND");
		assertThat(putQuestions(other, this.posting, "[]")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(setCards(other, this.posting, question, "[]")).hasStatus(HttpStatus.NOT_FOUND).bodyJson()
			.extractingPath("$.code").isEqualTo("POSTING_NOT_FOUND");
		assertThat(this.claude.calls()).isZero();
		assertThat(questionIds(this.posting)).containsExactly(question);
	}

	/** 문항이 없거나 카드가 없으면 409 이고 LLM 을 부르지 않는다. 저장 전 문항도 저장하지 않는다. */
	@Test
	void noQuestionsOrNoCardsIs409WithoutCallingTheLlm() {
		long bare = myPosting(this.me, NO_QUESTIONS);
		long unanalysed = myPosting(this.me, null);
		long empty = this.fixtures.user("라온");
		long hers = myPosting(empty, ANALYSIS);

		assertThat(match(this.me, bare)).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
			.isEqualTo("NO_QUESTIONS");
		assertThat(match(this.me, unanalysed)).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
			.isEqualTo("NO_QUESTIONS");
		assertThat(match(empty, hers)).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
			.isEqualTo("NO_CARDS");
		assertThat(this.claude.calls()).isZero();
		assertThat(questionIds(hers)).isEmpty();
	}

	/** 카드를 지우면 문항의 카드에서 빠지고, 내 공고에서 빼면 문항과 카드가 모두 지워진다. */
	@Test
	void deletingACardOrRemovingThePostingDropsTheRows() {
		long question = saveQuestions(this.me, this.posting, "[ { \"text\": \"직무 경험\" } ]").getFirst();
		assertThat(setCards(this.me, this.posting, question, "[%d, %d]".formatted(this.api, this.batch))).hasStatusOk();

		assertThat(this.mvc.delete().uri("/api/cards/{id}", this.api).with(loginAs(this.me)).with(xsrf()))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.mvc.get().uri("/api/me/postings/{id}", this.posting).with(loginAs(this.me))).hasStatusOk()
			.bodyJson().extractingPath("$.posting.questions[0].cards[*].cardId").isEqualTo(List.of((int) this.batch));
		assertThat(this.mvc.delete().uri("/api/me/postings/{id}", this.posting).with(loginAs(this.me)).with(xsrf()))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(questionIds(this.posting)).isEmpty();
		assertThat(this.jdbc.sql("select count(*) from user_posting_question_cards where question_id = :id")
			.param("id", question).query(Long.class).single()).isZero();
	}

	private long spent(long userId) {
		return this.jdbc.sql("select coalesce(sum(cost_micros), 0) from llm_usage where user_id = :userId")
			.param("userId", userId)
			.query(Long.class)
			.single();
	}

	/** 내 공고 상세의 JSON. */
	private org.springframework.test.json.AbstractJsonContentAssert<?> questions(long userId, long postingId) {
		return assertThat(this.mvc.get().uri("/api/me/postings/{id}", postingId).with(loginAs(userId))).hasStatusOk()
			.bodyJson();
	}

	private MvcTestResult match(long userId, long postingId) {
		return this.mvc.post()
			.uri("/api/me/postings/{id}/questions/match", postingId)
			.with(loginAs(userId))
			.with(xsrf())
			.exchange();
	}

	private MvcTestResult putQuestions(long userId, long postingId, String questions) {
		return this.mvc.put()
			.uri("/api/me/postings/{id}/questions", postingId)
			.with(loginAs(userId))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"questions\": " + questions + " }")
			.exchange();
	}

	private MvcTestResult setCards(long userId, long postingId, long questionId, String cardIds) {
		return this.mvc.put()
			.uri("/api/me/postings/{id}/questions/{questionId}/cards", postingId, questionId)
			.with(loginAs(userId))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"cardIds\": " + cardIds + " }")
			.exchange();
	}

	/** 문항을 두고 순서대로 id 를 돌려준다. */
	private List<Long> saveQuestions(long userId, long postingId, String questions) {
		assertThat(putQuestions(userId, postingId, questions)).hasStatusOk();
		return questionIds(postingId);
	}

	private List<Long> questionIds(long postingId) {
		return this.jdbc.sql("select id from user_posting_questions where posting_id = :id order by position")
			.param("id", postingId)
			.query(Long.class)
			.list();
	}

	/** 내 공고 하나. analysis 가 null 이면 분석이 없는 공고다. */
	private long myPosting(long userId, String analysis) {
		long postingId = this.fixtures.posting("예시랩", "백엔드 개발자", "UNKNOWN", null);
		this.fixtures.status(userId, postingId, "UNDECIDED", null, null);
		this.jdbc.sql("update postings set analysis = cast(:analysis as jsonb) where id = :id")
			.param("id", postingId)
			.param("analysis", analysis)
			.update();
		return postingId;
	}

	private long card(long userId, String title) {
		assertThat(this.mvc.post()
			.uri("/api/cards")
			.with(loginAs(userId))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"title\": \"%s\" }".formatted(title))).hasStatus(HttpStatus.CREATED);
		return this.jdbc.sql("select max(id) from experience_cards where user_id = :userId")
			.param("userId", userId)
			.query(Long.class)
			.single();
	}

}
