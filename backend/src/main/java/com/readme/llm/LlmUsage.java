package com.readme.llm;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * LLM 시도 한 번의 기록(llm_usage). 넣기만 한다. 사람이 탈퇴하면 user_id 만 빈다(테이블의 on delete set null).
 */
@Entity
@Table(name = "llm_usage")
class LlmUsage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	/** LlmFeature 의 상수 이름 */
	private String feature;

	private String model;

	private int inputTokens;

	private int outputTokens;

	@Column(name = "cache_write_5m_tokens")
	private int cacheWrite5mTokens;

	@Column(name = "cache_write_1h_tokens")
	private int cacheWrite1hTokens;

	private int cacheReadTokens;

	/** 백만분의 1달러 */
	private long costMicros;

	private Instant createdAt;

	protected LlmUsage() {
	}

	LlmUsage(long userId, LlmFeature feature, ClaudeApi.LlmAttempt attempt, long costMicros, Instant createdAt) {
		this.userId = userId;
		this.feature = feature.name();
		this.model = attempt.model();
		this.inputTokens = Math.toIntExact(attempt.inputTokens());
		this.outputTokens = Math.toIntExact(attempt.outputTokens());
		this.cacheWrite5mTokens = Math.toIntExact(attempt.cacheWrite5mTokens());
		this.cacheWrite1hTokens = Math.toIntExact(attempt.cacheWrite1hTokens());
		this.cacheReadTokens = Math.toIntExact(attempt.cacheReadTokens());
		this.costMicros = costMicros;
		this.createdAt = createdAt;
	}

}
