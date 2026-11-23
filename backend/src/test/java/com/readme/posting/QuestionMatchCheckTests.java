package com.readme.posting;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.readme.posting.PostingQuestions.Answer;
import com.readme.posting.PostingQuestions.Match;
import com.readme.posting.PostingQuestions.Pick;

import static org.assertj.core.api.Assertions.assertThat;

/** 문항 매칭의 LLM 답 검사(문항 매칭 설계 4.2). Spring 없이 본다. */
class QuestionMatchCheckTests {

	private static final Set<Long> MINE = Set.of(1L, 2L, 3L);

	@Test
	void dropsForeignAndDuplicateCardsAndKeepsTwo() {
		Map<Long, Match> kept = PostingQuestions.check(answer(new Match(10, "JOB", List.of(pick(99), pick(1), pick(1),
				pick(2), pick(3)))), limits(10L, 800), MINE);

		assertThat(kept.get(10L).picks()).extracting(Pick::cardId).containsExactly(1L, 2L);
	}

	/** 글자 수가 500 아래면 1장, 500 이상이거나 없으면 2장. */
	@Test
	void shortQuestionsGetOneCard() {
		Map<Long, Integer> limits = limits(10L, 499);
		limits.put(11L, 500);
		limits.put(12L, null);
		Map<Long, Match> kept = PostingQuestions.check(answer(new Match(10, "JOB", List.of(pick(1), pick(2))),
				new Match(11, "JOB", List.of(pick(1), pick(2))), new Match(12, "JOB", List.of(pick(1), pick(2)))), limits,
				MINE);

		assertThat(kept.get(10L).picks()).hasSize(1);
		assertThat(kept.get(11L).picks()).hasSize(2);
		assertThat(kept.get(12L).picks()).hasSize(2);
	}

	/** 아홉 밖의 유형은 null, 여섯 밖의 칸과 겹친 칸은 버리고 셋까지. 이유는 다듬고 200자에서 자른다. */
	@Test
	void dropsUnknownTypesAndFields() {
		Map<Long, Integer> limits = limits(10L, null);
		limits.put(11L, null);
		Map<Long, Match> kept = PostingQuestions.check(answer(
				new Match(10, "LEADERSHIP",
						List.of(new Pick(1, "  " + "가".repeat(250), Arrays.asList("obstacle", "feeling", null, "obstacle",
								"learning", "action", "result")))),
				new Match(11, null, List.of(new Pick(2, null, null)))), limits, MINE);

		assertThat(kept.get(10L).type()).isNull();
		assertThat(kept.get(10L).picks().getFirst().emphasis()).containsExactly("obstacle", "learning", "action");
		assertThat(kept.get(10L).picks().getFirst().reason()).hasSize(200).startsWith("가");
		assertThat(kept.get(11L).type()).isNull();
		assertThat(kept.get(11L).picks().getFirst().emphasis()).isEmpty();
		assertThat(kept.get(11L).picks().getFirst().reason()).isNull();
	}

	/** 보내지 않은 문항의 답, 같은 문항의 두 번째 답은 버린다. 문항끼리 겹친 카드는 둔다. */
	@Test
	void dropsUnknownQuestionsAndKeepsOverlapsAcrossQuestions() {
		Map<Long, Integer> limits = limits(10L, null);
		limits.put(11L, null);
		Map<Long, Match> kept = PostingQuestions.check(answer(new Match(10, "FAILURE", List.of(pick(1))),
				new Match(10, "JOB", List.of(pick(2))), new Match(77, "JOB", List.of(pick(3))),
				new Match(11, "MOTIVE", List.of(pick(1)))), limits, MINE);

		assertThat(kept).containsOnlyKeys(10L, 11L);
		assertThat(kept.get(10L).type()).isEqualTo("FAILURE");
		assertThat(kept.get(10L).picks()).extracting(Pick::cardId).containsExactly(1L);
		assertThat(kept.get(11L).picks()).extracting(Pick::cardId).containsExactly(1L);
	}

	@Test
	void emptyAnswerKeepsNothing() {
		assertThat(PostingQuestions.check(new Answer(null), limits(10L, null), MINE)).isEmpty();
		assertThat(PostingQuestions.check(answer(new Match(10, "MOTIVE", null)), limits(10L, null), MINE).get(10L).picks())
			.isEmpty();
	}

	private static Answer answer(Match... matches) {
		return new Answer(List.of(matches));
	}

	private static Pick pick(long cardId) {
		return new Pick(cardId, "이유", List.of("action"));
	}

	private static Map<Long, Integer> limits(Long id, Integer limit) {
		Map<Long, Integer> limits = new HashMap<>();
		limits.put(id, limit);
		return limits;
	}

}
