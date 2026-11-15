package com.readme.llm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Map;

import com.readme.llm.ClaudeApi.LlmAttempt;
import com.readme.llm.LlmProperties.LlmPrice;

/**
 * 시도 한 번의 금액. 단가는 백만 토큰당 달러라서 토큰 수 × 단가가 곧 백만분의 1달러(micros)다.
 *
 * <p>단가표에 없는 모델(서버 쪽 fallback 이 새 모델로 넘긴 경우)은 표에서 가장 비싼 단가로 센다. 한도를 넘기지 않는 쪽으로 틀린다.
 */
final class LlmPrices {

	private final Map<String, LlmPrice> prices;

	private final LlmPrice highest;

	LlmPrices(Map<String, LlmPrice> prices) {
		if (prices == null || prices.isEmpty()) {
			throw new IllegalStateException("app.llm.prices 가 비어 있다");
		}
		this.prices = Map.copyOf(prices);
		this.highest = prices.values()
			.stream()
			.max(Comparator.comparing(LlmPrice::output).thenComparing(LlmPrice::input))
			.orElseThrow();
	}

	boolean knows(String model) {
		return this.prices.containsKey(model);
	}

	/** 백만분의 1달러. 소수는 위로 올린다. */
	long costMicros(LlmAttempt attempt) {
		LlmPrice price = this.prices.getOrDefault(attempt.model(), this.highest);
		BigDecimal micros = price.input()
			.multiply(BigDecimal.valueOf(attempt.inputTokens()))
			.add(price.output().multiply(BigDecimal.valueOf(attempt.outputTokens())))
			.add(price.cacheWrite5m().multiply(BigDecimal.valueOf(attempt.cacheWrite5mTokens())))
			.add(price.cacheWrite1h().multiply(BigDecimal.valueOf(attempt.cacheWrite1hTokens())))
			.add(price.cacheRead().multiply(BigDecimal.valueOf(attempt.cacheReadTokens())));
		return micros.setScale(0, RoundingMode.CEILING).longValueExact();
	}

}
