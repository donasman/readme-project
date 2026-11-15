package com.readme.llm;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.readme.llm.ClaudeApi.LlmAttempt;

/**
 * 시도마다 llm_usage 에 한 행. 이미 청구된 호출이라, 부른 쪽의 트랜잭션이 되돌려져도 기록은 남게 따로 커밋한다.
 */
@Component
class LlmUsageRecorder {

	private static final Logger log = LoggerFactory.getLogger(LlmUsageRecorder.class);

	private final LlmUsageRepository repository;

	private final LlmPrices prices;

	private final Clock clock;

	LlmUsageRecorder(LlmUsageRepository repository, LlmPrices prices, Clock clock) {
		this.repository = repository;
		this.prices = prices;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void record(long userId, LlmFeature feature, List<LlmAttempt> attempts) {
		Instant now = this.clock.instant();
		for (LlmAttempt attempt : attempts) {
			if (!this.prices.knows(attempt.model())) {
				log.warn("단가표(app.llm.prices)에 없는 모델 {} 을 가장 비싼 단가로 셌다", attempt.model());
			}
			this.repository.save(new LlmUsage(userId, feature, attempt, this.prices.costMicros(attempt), now));
		}
	}

}
