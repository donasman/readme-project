package com.readme.posting;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;
import com.readme.common.token.Tokens;
import com.readme.llm.Llm;
import com.readme.llm.LlmFeature;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 내 공고마다의 경험카드 우선순위(카드 우선순위 설계). 초안은 LLM 이 고르고, 그 뒤에는 사용자가 순서를 정해 통째로 저장한다.
 *
 * <ul>
 * <li>초안은 사용자가 누를 때만 만든다. 한도는 누를 때 보고, LLM 은 트랜잭션 밖에서 부른다. 저장할 때 앞 목록을 바꾼다.</li>
 * <li>LLM 이 고른 id 중 내 카드가 아닌 것과 겹친 것은 버리고 {@link #MAX}장까지만 둔다.</li>
 * <li>저장은 내 공고 행을 잠그고 지운 뒤 다시 넣는다. 같은 사람이 두 번 함께 눌러도 뒤의 것 하나만 남는다.</li>
 * <li>LLM 의 답과 입력 key 를 목록과 함께 저장한다. 입력이 같으면 다음 초안은 LLM 을 부르지 않고 그 답을 같은 길로 다시 거른다.</li>
 * </ul>
 */
@Component
class CardPriorities {

	/** 목록에 두는 카드 수. 초안도 사용자가 고친 목록도 같다 */
	static final int MAX = 5;

	private static final int REASON_CHARS = 200;

	private static final String PROMPT = prompt();

	private static final TypeReference<List<PostSection>> SECTIONS = new TypeReference<>() {
	};

	private final Llm llm;

	private final PromptCards promptCards;

	private final UserPostingRepository repository;

	private final JdbcClient jdbc;

	private final JsonMapper jsonMapper;

	private final TransactionTemplate transaction;

	CardPriorities(Llm llm, PromptCards promptCards, UserPostingRepository repository, JdbcClient jdbc,
			JsonMapper jsonMapper, TransactionTemplate transaction) {
		this.llm = llm;
		this.promptCards = promptCards;
		this.repository = repository;
		this.jdbc = jdbc;
		this.jsonMapper = jsonMapper;
		this.transaction = transaction;
	}

	/** LLM 이 고른 카드 한 장. */
	record Pick(long cardId, String reason) {
	}

	/** LLM 의 답(지시문 prompts/card-priority.md). picks 는 먼저 쓸 카드부터다. */
	record Answer(List<Pick> picks) {
	}

	/**
	 * LLM 초안을 만들어 앞 목록을 바꾼다. 내 공고가 아니면 404 POSTING_NOT_FOUND, 카드가 없으면 409 NO_CARDS, 공고 분석도 내용 묶음도
	 * 없으면 409 NO_POSTING_CONTENT, 한도에 닿았으면 429 LLM_LIMIT_REACHED. 저장한 답을 다시 썼으면 true.
	 */
	boolean draft(long userId, long postingId) {
		Posting posting = posting(userId, postingId);
		List<PromptCards.Card> cards = this.promptCards.of(userId);
		if (cards.isEmpty()) {
			throw new ApiException(PostingErrorCode.NO_CARDS);
		}
		String postingText = posting.text();
		if (postingText.isBlank()) {
			throw new ApiException(PostingErrorCode.NO_POSTING_CONTENT);
		}
		String text = postingText + "\n\n" + cards.stream().map(PromptCards.Card::text).collect(Collectors.joining("\n\n"));
		String key = answerKey(PROMPT, text);
		// 입력이 같으면 저장한 답을 다시 쓴다(2026-11-24 사용자 결정). 한도를 보지 않고 사용액도 들지 않는다
		Answer answer = key.equals(posting.answerKey()) ? storedAnswer(this.jsonMapper, posting.answer(), Answer.class) : null;
		boolean reused = answer != null;
		if (!reused) {
			this.llm.requireBudget(userId);
			answer = this.llm.ask(userId, LlmFeature.CARD_PRIORITY, PROMPT, text, Answer.class);
		}
		String answerJson = this.jsonMapper.writeValueAsString(answer);
		Set<Long> mine = cards.stream().map(PromptCards.Card::id).collect(Collectors.toSet());
		Map<Long, String> picked = new LinkedHashMap<>();
		for (Pick pick : (answer.picks() != null) ? answer.picks() : List.<Pick>of()) {
			if (picked.size() < MAX && mine.contains(pick.cardId())) {
				picked.putIfAbsent(pick.cardId(), PromptCards.cut(pick.reason(), REASON_CHARS));
			}
		}
		save(userId, postingId, picked, () -> this.repository.saveCardPriorityAnswer(userId, postingId, key, answerJson));
		return reused;
	}

	/**
	 * 사용자가 정한 순서로 바꾼다. 앞 목록에 있던 카드는 고른 이유를 그대로 둔다. 겹친 id, 내 카드가 아닌 id 는 400 INVALID_INPUT.
	 * 내 공고가 아니면 404 POSTING_NOT_FOUND.
	 */
	void reorder(long userId, long postingId, List<Long> cardIds) {
		if (new HashSet<>(cardIds).size() != cardIds.size()) {
			throw new ApiException(CommonErrorCode.INVALID_INPUT, "같은 카드를 두 번 넣을 수 없습니다");
		}
		if (!cardIds.isEmpty() && this.jdbc.sql("select count(*) from experience_cards where user_id = :userId and id in (:ids)")
			.param("userId", userId)
			.param("ids", cardIds)
			.query(Long.class)
			.single() != cardIds.size()) {
			throw new ApiException(CommonErrorCode.INVALID_INPUT, "내 경험카드만 넣을 수 있습니다");
		}
		Map<Long, String> reasons = this.jdbc
			.sql("select card_id, reason from user_posting_cards where user_id = :userId and posting_id = :postingId")
			.param("userId", userId)
			.param("postingId", postingId)
			.query((rs, rowNum) -> new String[] { rs.getString("card_id"), rs.getString("reason") })
			.list()
			.stream()
			.filter((row) -> row[1] != null)
			.collect(Collectors.toMap((row) -> Long.parseLong(row[0]), (row) -> row[1]));
		Map<Long, String> ordered = new LinkedHashMap<>();
		cardIds.forEach((id) -> ordered.put(id, reasons.get(id)));
		save(userId, postingId, ordered, () -> {
		});
	}

	/** also 는 목록과 같은 트랜잭션에서 돈다(초안의 입력 key 와 답 저장). */
	private void save(long userId, long postingId, Map<Long, String> cards, Runnable also) {
		this.transaction.executeWithoutResult((status) -> {
			// 내 공고 행을 잠가 같은 목록을 함께 바꾸는 두 요청이 차례로 돌게 한다. 그 사이 빠졌으면 404
			if (this.jdbc.sql("select 1 from user_postings where user_id = :userId and posting_id = :postingId for update")
				.param("userId", userId)
				.param("postingId", postingId)
				.query(Integer.class)
				.optional()
				.isEmpty()) {
				throw new ApiException(PostingErrorCode.POSTING_NOT_FOUND);
			}
			this.repository.deleteCards(userId, postingId);
			int position = 0;
			for (Map.Entry<Long, String> card : cards.entrySet()) {
				// 지워진 카드는 넣지 않으므로 자리도 건너뛰지 않는다
				position += this.repository.insertCard(userId, postingId, card.getKey(), position, card.getValue());
			}
			also.run();
		});
	}

	private Posting posting(long userId, long postingId) {
		return this.jdbc.sql("""
				select p.company, p.title, p.sections::text as sections, p.analysis::text as analysis,
				       up.card_priority_key, up.card_priority_answer::text as card_priority_answer
				  from user_postings up join postings p on p.id = up.posting_id
				 where up.user_id = :userId and up.posting_id = :postingId
				""")
			.param("userId", userId)
			.param("postingId", postingId)
			.query((rs, rowNum) -> new Posting(rs.getString("company"), rs.getString("title"),
					this.jsonMapper.readValue(rs.getString("sections"), SECTIONS),
					(rs.getString("analysis") != null)
							? this.jsonMapper.readValue(rs.getString("analysis"), PostingAnalysis.class) : null,
					rs.getString("card_priority_key"), rs.getString("card_priority_answer")))
			.optional()
			.orElseThrow(() -> new ApiException(PostingErrorCode.POSTING_NOT_FOUND));
	}

	/**
	 * 초안에 쓰는 공고 본체의 값. 공고 분석이 있으면 분석을, 없으면 내용 묶음을 보낸다. answerKey 와 answer 는 앞 초안의 입력 key 와
	 * LLM 답(json)이다. 없으면 null.
	 */
	private record Posting(String company, String title, List<PostSection> sections, PostingAnalysis analysis,
			String answerKey, String answer) {

		String text() {
			String body;
			if (this.analysis != null) {
				body = analysisText(this.analysis);
			}
			else {
				body = this.sections.isEmpty() ? "" : PostingAnalysisRules.maskContacts(PostingAnalysisRules.posting(this.sections));
			}
			return body.isBlank() ? "" : "## 공고 — " + this.company + " / " + this.title + "\n" + body.strip() + "\n\n## 내 경험카드";
		}

	}

	/** 공고 분석의 필수·우대·업무·키워드. 빈 칸은 뺀다. 문항 매칭(QD7)도 쓴다. */
	static String analysisText(PostingAnalysis analysis) {
		return part("필수 요건", analysis.must()) + part("우대 사항", analysis.nice()) + part("주요 업무", analysis.duties())
				+ part("키워드", analysis.keywords());
	}

	/** LLM 입력의 key. 보낸 지시문과 사용자 글이 같으면 같다(2026-11-24 사용자 결정). 문항 매칭도 쓴다. */
	static String answerKey(String system, String text) {
		return Tokens.sha256Hex(system + "\0" + text);
	}

	/** 저장한 LLM 답. 없거나 지금의 답 모양으로 읽지 못하면 null 이라 LLM 을 다시 부른다. 문항 매칭도 쓴다. */
	static <T> T storedAnswer(JsonMapper jsonMapper, String json, Class<T> type) {
		if (json == null) {
			return null;
		}
		try {
			return jsonMapper.readValue(json, type);
		}
		catch (JacksonException ex) {
			return null;
		}
	}

	private static String part(String title, List<String> items) {
		return items.isEmpty() ? "" : "### " + title + "\n" + items.stream().map((i) -> "- " + i).collect(Collectors.joining("\n")) + "\n";
	}

	private static String prompt() {
		try (InputStream in = new ClassPathResource("prompts/card-priority.md").getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("프롬프트를 읽지 못했다: card-priority.md", ex);
		}
	}

}
