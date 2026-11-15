package com.readme.llm;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.readme.FixedClockConfiguration;
import com.readme.Fixtures;
import com.readme.IntegrationTest;
import com.readme.common.error.ApiException;
import com.readme.llm.ClaudeApi.ClaudeReply;
import com.readme.llm.ClaudeApi.LlmAttempt;
import com.readme.llm.ClaudeApi.Outcome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * 묻기와 사용액 기록, 한 달 한도. 시계는 2026-11-13 00:30(서울)이라 이번 달은 2026-11-01 00:00(서울) = 10-31 15:00Z 부터다.
 */
@IntegrationTest
class LlmTests {

	record Answer(String summary) {
	}

	@Autowired
	private Llm llm;

	@Autowired
	private FakeClaudeApi claude;

	@Autowired
	private Fixtures fixtures;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void reset() {
		this.claude.reset();
	}

	@Test
	void askReturnsTheAnswerAndRecordsEachAttempt() {
		long userId = this.fixtures.user("민지");
		// 서버 쪽 fallback: Opus 5.5 가 답하기 전에 거절하고 Opus 4.8 이 답했다
		this.claude.willReply(ClaudeReply.done(new Answer("요약"),
				List.of(new LlmAttempt("claude-opus-5-5", 1000, 0, 0, 0, 0),
						new LlmAttempt("claude-opus-4-8", 1000, 500, 200, 0, 300))));

		Answer answer = this.llm.ask(userId, LlmFeature.POSTING_ANALYSIS, "지시문", "공고 본문", Answer.class);

		assertThat(answer).isEqualTo(new Answer("요약"));
		assertThat(this.claude.requests()).singleElement().satisfies((request) -> {
			assertThat(request.model()).isEqualTo("claude-opus-5-5");
			assertThat(request.feature()).isEqualTo(LlmFeature.POSTING_ANALYSIS);
			assertThat(request.system()).isEqualTo("지시문");
			assertThat(request.text()).isEqualTo("공고 본문");
		});
		assertThat(rows(userId)).containsExactly(
				new Row("POSTING_ANALYSIS", "claude-opus-5-5", 1000, 0, 0, 0, 4000, FixedClockConfiguration.NOW),
				// 1000×5 + 500×25 + 200×6.25 + 300×0.5
				new Row("POSTING_ANALYSIS", "claude-opus-4-8", 1000, 500, 200, 300, 18_900, FixedClockConfiguration.NOW));
	}

	@Test
	void unusableAnswerFailsButIsStillCharged() {
		long userId = this.fixtures.user("민지");
		this.claude.willReply(
				ClaudeReply.failed(Outcome.REFUSED, List.of(new LlmAttempt("claude-opus-5-5", 800, 20, 0, 0, 0))));

		assertThatExceptionOfType(ApiException.class)
			.isThrownBy(() -> this.llm.ask(userId, LlmFeature.CARD_EXTRACT, null, "자료", Answer.class))
			.satisfies((ex) -> {
				assertThat(ex.errorCode()).isEqualTo(LlmErrorCode.LLM_FAILED);
				assertThat(ex.errorCode().status()).isEqualTo(HttpStatus.BAD_GATEWAY);
			});
		assertThat(rows(userId)).extracting(Row::costMicros).containsExactly(800L * 4 + 20 * 20);
	}

	@Test
	void budgetCountsOnlyThisMonthInSeoul() {
		long userId = this.fixtures.user("민지");
		// 10-31 23:59:59(서울)은 지난달이다
		usage(userId, 5_000_000, Instant.parse("2026-10-31T14:59:59Z"));
		usage(userId, 1_999_999, Instant.parse("2026-10-31T15:00:00Z"));

		assertThatNoException().isThrownBy(() -> this.llm.requireBudget(userId));

		usage(userId, 1, FixedClockConfiguration.NOW);

		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> this.llm.requireBudget(userId))
			.satisfies((ex) -> {
				assertThat(ex.errorCode()).isEqualTo(LlmErrorCode.LLM_LIMIT_REACHED);
				assertThat(ex.errorCode().status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
			});
	}

	@Test
	void budgetIsPerPerson() {
		long spender = this.fixtures.user("민지");
		long other = this.fixtures.user("서준");
		usage(spender, 2_000_000, FixedClockConfiguration.NOW);

		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> this.llm.requireBudget(spender));
		assertThatNoException().isThrownBy(() -> this.llm.requireBudget(other));
	}

	record Row(String feature, String model, int inputTokens, int outputTokens, int cacheWrite5mTokens,
			int cacheReadTokens, long costMicros, Instant createdAt) {
	}

	private List<Row> rows(long userId) {
		return this.jdbc.sql("""
				select feature, model, input_tokens, output_tokens, cache_write_5m_tokens, cache_read_tokens, cost_micros,
				       created_at
				  from llm_usage where user_id = :userId order by id
				""")
			.param("userId", userId)
			.query((rs, rowNum) -> new Row(rs.getString("feature"), rs.getString("model"), rs.getInt("input_tokens"),
					rs.getInt("output_tokens"), rs.getInt("cache_write_5m_tokens"), rs.getInt("cache_read_tokens"),
					rs.getLong("cost_micros"), rs.getTimestamp("created_at").toInstant()))
			.list();
	}

	private void usage(long userId, long costMicros, Instant at) {
		this.jdbc.sql("""
				insert into llm_usage (user_id, feature, model, input_tokens, output_tokens, cost_micros, created_at)
				values (:userId, 'CARD_EXTRACT', 'claude-opus-5-5', 0, 0, :cost, :at)
				""").param("userId", userId).param("cost", costMicros).param("at", Timestamp.from(at)).update();
	}

}
