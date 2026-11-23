package com.readme.posting;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;
import com.readme.llm.Llm;
import com.readme.llm.LlmFeature;
import com.readme.posting.MyPostingDetailResponse.Question;

import tools.jackson.databind.json.JsonMapper;

/**
 * 내 공고의 자기소개서 문항과 문항마다 고른 경험카드(문항 매칭 설계). 문항은 공고 분석의 것이 처음 값이고, 사용자가 고치면 내 목록을
 * 저장해 쓴다. 카드는 LLM 이 고르고 사용자가 바꾼다.
 *
 * <ul>
 * <li>고르기는 사용자가 누를 때만 한다. 한도는 누를 때 보고, LLM 은 트랜잭션 밖에서 부른다(카드 우선순위와 같다).</li>
 * <li>LLM 의 답은 {@link #check}로 거른다: 내 카드가 아닌 id, 한 문항 안에서 겹친 id, 아홉 밖의 유형, 여섯 밖의 칸을 버린다.</li>
 * <li>쓰기는 모두 내 공고 행을 잠근 한 트랜잭션에서 한다. 같은 사람이 함께 눌러도 차례로 돈다.</li>
 * </ul>
 */
@Component
class PostingQuestions {

	private static final Logger log = LoggerFactory.getLogger(PostingQuestions.class);

	/** 문항 수(QD6) */
	static final int MAX_QUESTIONS = 10;

	/** 문항 글의 길이(QD6). user_posting_questions.text 칸과 같다 */
	static final int TEXT_CHARS = 1000;

	/** 글자 수의 상한(QD6) */
	static final int MAX_LIMIT = 10_000;

	/** 문항마다 카드 수(QD1). 글자 수가 {@link #SHORT_LIMIT} 아래인 문항은 1장 */
	static final int MAX_CARDS = 2;

	private static final int SHORT_LIMIT = 500;

	/** 문항 유형(QD8, 리서치 3절) */
	static final Set<String> TYPES = Set.of("JOB", "PROBLEM", "GOAL", "TEAM", "FAILURE", "GROWTH", "VALUES", "MOTIVE",
			"FUTURE");

	/** 살릴 칸(QD9). 카드의 칸 이름이다 */
	static final Set<String> FIELDS = Set.of("situation", "task", "action", "result", "obstacle", "learning");

	private static final int MAX_FIELDS = 3;

	private static final int REASON_CHARS = 200;

	private static final String PROMPT = prompt();

	private final Llm llm;

	private final PromptCards promptCards;

	private final MyPostingQueries queries;

	private final UserPostingRepository repository;

	private final JdbcClient jdbc;

	private final JsonMapper jsonMapper;

	private final TransactionTemplate transaction;

	PostingQuestions(Llm llm, PromptCards promptCards, MyPostingQueries queries, UserPostingRepository repository,
			JdbcClient jdbc, JsonMapper jsonMapper, TransactionTemplate transaction) {
		this.llm = llm;
		this.promptCards = promptCards;
		this.queries = queries;
		this.repository = repository;
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
		this.transaction = transaction;
	}

	/**
	 * 문항 한 줄의 입력. id 가 있으면 그 문항을 고치고(유형과 카드가 남는다), 없으면 새 문항이다.
	 *
	 * @param limit 글자 수. 없으면 null
	 */
	record Input(Long id, @NotBlank @Size(max = TEXT_CHARS) String text, @Min(1) @Max(MAX_LIMIT) Integer limit) {
	}

	/** LLM 이 문항에 고른 카드 한 장. emphasis 는 살릴 칸이다. */
	record Pick(long cardId, String reason, List<String> emphasis) {
	}

	/** LLM 이 문항 하나에 한 답. type 은 아홉 유형 가운데 하나다. */
	record Match(long questionId, String type, List<Pick> picks) {
	}

	/** LLM 의 답(지시문 prompts/question-match.md). */
	record Answer(List<Match> questions) {
	}

	/**
	 * 문항 목록을 이 순서로 통째로 둔다. 빠진 문항은 지운다(그 카드도). 같은 id 를 두 번 넣거나 이 공고의 내 문항이 아닌 id 는 400
	 * INVALID_INPUT, 내 공고가 아니면 404 POSTING_NOT_FOUND.
	 */
	void replace(long userId, long postingId, List<Input> questions) {
		List<Long> ids = questions.stream().map(Input::id).filter(Objects::nonNull).toList();
		if (new HashSet<>(ids).size() != ids.size()) {
			throw new ApiException(CommonErrorCode.INVALID_INPUT, "같은 문항을 두 번 넣을 수 없습니다");
		}
		this.transaction.executeWithoutResult((status) -> {
			lock(userId, postingId);
			Set<Long> existing = questionLimits(userId, postingId).keySet();
			if (!existing.containsAll(ids)) {
				throw new ApiException(CommonErrorCode.INVALID_INPUT, "이 공고의 문항이 아닙니다");
			}
			existing.stream().filter((id) -> !ids.contains(id)).forEach(this.repository::deleteQuestion);
			int position = 0;
			for (Input question : questions) {
				if (question.id() != null) {
					this.repository.updateQuestion(question.id(), position++, question.text().strip(), question.limit());
				}
				else {
					this.repository.insertQuestion(userId, postingId, position++, question.text().strip(), question.limit());
				}
			}
			this.repository.markQuestionsSaved(userId, postingId);
		});
	}

	/**
	 * 문항의 카드를 이 순서로 둔다(QD1 의 수까지, 빈 목록이면 비운다). 앞에 있던 카드는 이유와 살릴 칸이 남는다. 내 공고가 아니면 404
	 * POSTING_NOT_FOUND, 이 공고의 내 문항이 아니면 404 QUESTION_NOT_FOUND, 수를 넘거나 겹치거나 내 카드가 아니면 400 INVALID_INPUT.
	 */
	void setCards(long userId, long postingId, long questionId, List<Long> cardIds) {
		this.transaction.executeWithoutResult((status) -> {
			lock(userId, postingId);
			Map<Long, Integer> limits = questionLimits(userId, postingId);
			if (!limits.containsKey(questionId)) {
				throw new ApiException(PostingErrorCode.QUESTION_NOT_FOUND);
			}
			if (cardIds.size() > maxCards(limits.get(questionId))) {
				throw new ApiException(CommonErrorCode.INVALID_INPUT,
						"이 문항에는 카드를 " + maxCards(limits.get(questionId)) + "장까지 둘 수 있습니다");
			}
			if (new HashSet<>(cardIds).size() != cardIds.size()) {
				throw new ApiException(CommonErrorCode.INVALID_INPUT, "같은 카드를 두 번 넣을 수 없습니다");
			}
			if (!cardIds.isEmpty() && this.jdbc
				.sql("select count(*) from experience_cards where user_id = :userId and id in (:ids)")
				.param("userId", userId)
				.param("ids", cardIds)
				.query(Long.class)
				.single() != cardIds.size()) {
				throw new ApiException(CommonErrorCode.INVALID_INPUT, "내 경험카드만 넣을 수 있습니다");
			}
			Map<Long, String[]> before = new HashMap<>();
			this.jdbc.sql("select card_id, reason, emphasis from user_posting_question_cards where question_id = :id")
				.param("id", questionId)
				.query((rs, rowNum) -> before.put(rs.getLong("card_id"),
						new String[] { rs.getString("reason"), rs.getString("emphasis") }))
				.list();
			this.repository.deleteQuestionCards(questionId);
			int position = 0;
			for (long cardId : cardIds) {
				String[] kept = before.getOrDefault(cardId, new String[2]);
				this.repository.insertQuestionCard(questionId, cardId, position++, kept[0], kept[1]);
			}
		});
	}

	/**
	 * LLM 이 모든 문항의 유형과 카드를 골라 앞 결과를 바꾼다(QD5). 저장 전이면 지금 보이는 문항(공고 분석의 것)을 먼저 저장한다. 내 공고가
	 * 아니면 404 POSTING_NOT_FOUND, 문항이 없으면 409 NO_QUESTIONS, 카드가 없으면 409 NO_CARDS, 한도에 닿았으면 429
	 * LLM_LIMIT_REACHED.
	 */
	void match(long userId, long postingId) {
		Posting posting = posting(userId, postingId);
		List<Question> visible = posting.questionsSaved() ? this.queries.questions(userId, postingId)
				: MyPostingQueries.analysisQuestions(posting.analysis());
		if (visible.isEmpty()) {
			throw new ApiException(PostingErrorCode.NO_QUESTIONS);
		}
		List<PromptCards.Card> cards = this.promptCards.of(userId);
		if (cards.isEmpty()) {
			throw new ApiException(PostingErrorCode.NO_CARDS);
		}
		this.llm.requireBudget(userId);
		if (!posting.questionsSaved()) {
			saveAnalysisQuestions(userId, postingId, visible);
		}
		List<Question> questions = this.queries.questions(userId, postingId);
		if (questions.isEmpty()) {
			throw new ApiException(PostingErrorCode.NO_QUESTIONS);
		}
		Answer answer = this.llm.ask(userId, LlmFeature.QUESTION_MATCH, PROMPT,
				text(posting, questions) + cards.stream().map(PromptCards.Card::text).collect(Collectors.joining("\n\n")),
				Answer.class);
		Map<Long, Integer> limits = new HashMap<>();
		questions.forEach((q) -> limits.put(q.id(), q.limit()));
		Map<Long, Match> matched = check(answer, limits,
				cards.stream().map(PromptCards.Card::id).collect(Collectors.toSet()));
		long overlaps = matched.values()
			.stream()
			.flatMap((m) -> m.picks().stream().map(Pick::cardId))
			.collect(Collectors.groupingBy((id) -> id, Collectors.counting()))
			.values()
			.stream()
			.filter((count) -> count > 1)
			.count();
		if (overlaps > 0) {
			log.info("문항 매칭: 공고 {} 에서 카드 {}장을 여러 문항에 골랐다", postingId, overlaps);
		}
		this.transaction.executeWithoutResult((status) -> {
			lock(userId, postingId);
			// 그 사이 지워진 문항의 답은 버린다. 그 사이 더한 문항은 LLM 이 보지 않았으므로 그대로 둔다
			Set<Long> existing = questionLimits(userId, postingId).keySet();
			for (Question question : questions) {
				if (!existing.contains(question.id())) {
					continue;
				}
				Match match = matched.get(question.id());
				this.repository.updateQuestionType(question.id(), (match != null) ? match.type() : null);
				this.repository.deleteQuestionCards(question.id());
				int position = 0;
				for (Pick pick : (match != null) ? match.picks() : List.<Pick>of()) {
					// LLM 을 기다리는 동안 지워진 카드는 넣지 않으므로 자리도 건너뛰지 않는다
					position += this.repository.insertQuestionCard(question.id(), pick.cardId(), position, pick.reason(),
							pick.emphasis().isEmpty() ? null : String.join(",", pick.emphasis()));
				}
			}
		});
	}

	/**
	 * LLM 의 답을 거른다(설계 4.2). 보낸 문항이 아닌 questionId, 한 번 답한 문항의 두 번째 답은 버린다. 문항마다 내 카드가 아닌 id 와
	 * 겹친 id 를 버리고 QD1 의 수까지 둔다. 아홉 밖의 유형은 null, 여섯 밖의 칸과 겹친 칸은 버리고 셋까지 둔다. 이유는 200자에서 자른다.
	 * 문항끼리 겹친 카드는 버리지 않는다.
	 *
	 * @param limits 보낸 문항의 id 와 글자 수(없으면 null)
	 * @param mine   LLM 에 보낸 내 카드 id
	 */
	static Map<Long, Match> check(Answer answer, Map<Long, Integer> limits, Set<Long> mine) {
		Map<Long, Match> kept = new LinkedHashMap<>();
		for (Match match : orEmpty(answer.questions())) {
			if (match == null || !limits.containsKey(match.questionId()) || kept.containsKey(match.questionId())) {
				continue;
			}
			int max = maxCards(limits.get(match.questionId()));
			Map<Long, Pick> picks = new LinkedHashMap<>();
			for (Pick pick : orEmpty(match.picks())) {
				if (pick != null && picks.size() < max && mine.contains(pick.cardId())) {
					picks.putIfAbsent(pick.cardId(),
							new Pick(pick.cardId(), PromptCards.cut(pick.reason(), REASON_CHARS),
									orEmpty(pick.emphasis()).stream()
										.filter((field) -> field != null && FIELDS.contains(field))
										.distinct()
										.limit(MAX_FIELDS)
										.toList()));
				}
			}
			kept.put(match.questionId(), new Match(match.questionId(), (match.type() != null && TYPES.contains(match.type())) ? match.type() : null,
					new ArrayList<>(picks.values())));
		}
		return kept;
	}

	/** 문항의 카드 수(QD1). 글자 수가 500자 아래면 1장, 아니면(없을 때도) 2장. */
	static int maxCards(Integer limit) {
		return (limit != null && limit < SHORT_LIMIT) ? 1 : MAX_CARDS;
	}

	/** 공고 분석의 문항을 내 문항으로 저장한다. 그 사이 다른 요청이 먼저 저장했으면 그대로 둔다. */
	private void saveAnalysisQuestions(long userId, long postingId, List<Question> questions) {
		this.transaction.executeWithoutResult((status) -> {
			lock(userId, postingId);
			if (this.jdbc
				.sql("select questions_saved from user_postings where user_id = :userId and posting_id = :postingId")
				.param("userId", userId)
				.param("postingId", postingId)
				.query(Boolean.class)
				.single()) {
				return;
			}
			int position = 0;
			for (Question question : questions) {
				this.repository.insertQuestion(userId, postingId, position++, question.text(), question.limit());
			}
			this.repository.markQuestionsSaved(userId, postingId);
		});
	}

	/** 내 공고 행을 잠근다. 같은 문항을 함께 바꾸는 두 요청이 차례로 돌게 한다. 그 사이 빠졌으면 404. */
	private void lock(long userId, long postingId) {
		if (this.jdbc.sql("select 1 from user_postings where user_id = :userId and posting_id = :postingId for update")
			.param("userId", userId)
			.param("postingId", postingId)
			.query(Integer.class)
			.optional()
			.isEmpty()) {
			throw new ApiException(PostingErrorCode.POSTING_NOT_FOUND);
		}
	}

	/** 이 공고의 내 문항 id 와 글자 수(없으면 null). 순서대로다. */
	private Map<Long, Integer> questionLimits(long userId, long postingId) {
		Map<Long, Integer> limits = new LinkedHashMap<>();
		this.jdbc.sql("""
				select id, char_limit from user_posting_questions
				 where user_id = :userId and posting_id = :postingId order by position, id
				""")
			.param("userId", userId)
			.param("postingId", postingId)
			.query((rs, rowNum) -> limits.put(rs.getLong("id"), rs.getObject("char_limit", Integer.class)))
			.list();
		return limits;
	}

	private Posting posting(long userId, long postingId) {
		return this.jdbc.sql("""
				select p.company, p.title, p.analysis::text as analysis, up.questions_saved
				  from user_postings up join postings p on p.id = up.posting_id
				 where up.user_id = :userId and up.posting_id = :postingId
				""")
			.param("userId", userId)
			.param("postingId", postingId)
			.query((rs, rowNum) -> new Posting(rs.getString("company"), rs.getString("title"),
					(rs.getString("analysis") != null)
							? this.jsonMapper.readValue(rs.getString("analysis"), PostingAnalysis.class) : null,
					rs.getBoolean("questions_saved")))
			.optional()
			.orElseThrow(() -> new ApiException(PostingErrorCode.POSTING_NOT_FOUND));
	}

	/** LLM 에 보내는 글의 앞: 공고 분석(있으면), 문항. 뒤에 내 카드가 붙는다(QD7). */
	private static String text(Posting posting, List<Question> questions) {
		StringBuilder text = new StringBuilder();
		String analysis = (posting.analysis() != null) ? CardPriorities.analysisText(posting.analysis()) : "";
		if (!analysis.isBlank()) {
			text.append("## 공고 — ").append(posting.company()).append(" / ").append(posting.title()).append('\n')
				.append(analysis.strip()).append("\n\n");
		}
		text.append("## 자기소개서 문항\n");
		for (Question question : questions) {
			text.append("### 문항 id ").append(question.id())
				.append((question.limit() != null) ? " (글자 수 " + question.limit() + "자)" : " (글자 수 없음)")
				.append('\n').append(question.text()).append("\n\n");
		}
		return text.append("## 내 경험카드\n").toString();
	}

	private static <T> List<T> orEmpty(List<T> list) {
		return (list != null) ? list : List.of();
	}

	/** 고르기에 쓰는 공고 본체의 값과 내 문항을 저장했는지. */
	private record Posting(String company, String title, PostingAnalysis analysis, boolean questionsSaved) {
	}

	private static String prompt() {
		try (InputStream in = new ClassPathResource("prompts/question-match.md").getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("프롬프트를 읽지 못했다: question-match.md", ex);
		}
	}

}
