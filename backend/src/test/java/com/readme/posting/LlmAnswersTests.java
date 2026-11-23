package com.readme.posting;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.readme.posting.CardPriorities.Answer;
import com.readme.posting.CardPriorities.Pick;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** 같은 입력이면 저장한 LLM 답을 다시 쓰기(2026-11-24 사용자 결정). Spring 없이 본다. */
class LlmAnswersTests {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	/** 모델, 지시문, 사용자 글 가운데 하나만 달라도 key 가 다르다. 경계를 옮겨도 다르다. */
	@Test
	void keyChangesWithTheModelThePromptOrTheText() {
		String key = LlmAnswers.key("claude-opus-5-5", "지시문", "글");

		assertThat(key).hasSize(64).isEqualTo(LlmAnswers.key("claude-opus-5-5", "지시문", "글"));
		assertThat(List.of(LlmAnswers.key("claude-sonnet-5", "지시문", "글"), LlmAnswers.key("claude-opus-5-5", "지시문2", "글"),
				LlmAnswers.key("claude-opus-5-5", "지시문", "글2"), LlmAnswers.key("claude-opus-5-5", "지시", "문글")))
			.doesNotContain(key);
	}

	@Test
	void unreadableStoredAnswerIsNull() {
		assertThat(LlmAnswers.stored(this.jsonMapper, "not json", Answer.class)).isNull();
		assertThat(LlmAnswers.stored(this.jsonMapper, "[1, 2]", Answer.class)).isNull();
		assertThat(LlmAnswers.stored(this.jsonMapper, null, Answer.class)).isNull();
	}

	/** key 가 같으면 묻지 않고 저장한 답을, 다르면 묻는다. 돌려준 json 을 다시 읽으면 같은 답이다. */
	@Test
	void reusesOnlyWhenTheKeyMatches() {
		Answer answer = new Answer(List.of(new Pick(3, "이유")));
		String json = this.jsonMapper.writeValueAsString(answer);
		AtomicInteger asked = new AtomicInteger();

		LlmAnswers.Reply<Answer> reused = LlmAnswers.reuseOrAsk(this.jsonMapper, "k", "k", json, Answer.class, () -> {
			asked.incrementAndGet();
			return null;
		});
		assertThat(reused.reused()).isTrue();
		assertThat(reused.answer()).isEqualTo(answer);
		assertThat(asked).hasValue(0);

		LlmAnswers.Reply<Answer> fresh = LlmAnswers.reuseOrAsk(this.jsonMapper, "k2", "k", json, Answer.class, () -> {
			asked.incrementAndGet();
			return new Answer(List.of());
		});
		assertThat(fresh.reused()).isFalse();
		assertThat(fresh.key()).isEqualTo("k2");
		assertThat(LlmAnswers.stored(this.jsonMapper, fresh.json(), Answer.class)).isEqualTo(new Answer(List.of()));
		assertThat(asked).hasValue(1);
	}

}
