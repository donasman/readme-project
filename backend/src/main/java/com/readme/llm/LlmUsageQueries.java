package com.readme.llm;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class LlmUsageQueries {

	private final JdbcClient jdbc;

	LlmUsageQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** since 부터 쓴 금액(백만분의 1달러). llm_usage_user_idx (user_id, created_at) 를 탄다. */
	long spentMicros(long userId, Instant since) {
		return this.jdbc.sql("""
				select coalesce(sum(cost_micros), 0)
				  from llm_usage
				 where user_id = :userId and created_at >= :since
				""").param("userId", userId).param("since", Timestamp.from(since)).query(Long.class).single();
	}

}
