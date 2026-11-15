package com.readme.llm;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.readme.llm.ClaudeApi.LlmAttempt;
import com.readme.llm.LlmProperties.LlmPrice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class LlmPricesTests {

	// application.yml 과 같은 값
	private final LlmPrices prices = new LlmPrices(Map.of("claude-opus-5-5", price("4", "20", "5", "8", "0.20"),
			"claude-opus-4-8", price("5", "25", "6.25", "10", "0.50")));

	@Test
	void sumsEveryKindOfToken() {
		// 1000×4 + 2000×20 + 100×5 + 10×8 + 3×0.2 = 44,580.6 → 위로 올려 44,581
		assertThat(this.prices.costMicros(new LlmAttempt("claude-opus-5-5", 1000, 2000, 100, 10, 3))).isEqualTo(44_581);
	}

	@Test
	void roundsUpOnlyFractions() {
		assertThat(this.prices.costMicros(new LlmAttempt("claude-opus-5-5", 0, 0, 0, 0, 5))).isEqualTo(1);
		assertThat(this.prices.costMicros(new LlmAttempt("claude-opus-5-5", 0, 0, 0, 0, 1))).isEqualTo(1);
		assertThat(this.prices.costMicros(new LlmAttempt("claude-opus-5-5", 0, 0, 0, 0, 0))).isZero();
	}

	@Test
	void eachModelUsesItsOwnPrice() {
		assertThat(this.prices.costMicros(new LlmAttempt("claude-opus-4-8", 1000, 500, 0, 0, 0)))
			.isEqualTo(1000 * 5 + 500 * 25);
	}

	@Test
	void unknownModelCostsTheHighestPrice() {
		assertThat(this.prices.knows("claude-new")).isFalse();
		assertThat(this.prices.costMicros(new LlmAttempt("claude-new", 1000, 0, 0, 0, 0))).isEqualTo(5000);
	}

	@Test
	void needsAtLeastOnePrice() {
		assertThatIllegalStateException().isThrownBy(() -> new LlmPrices(Map.of()));
	}

	private static LlmPrice price(String input, String output, String write5m, String write1h, String read) {
		return new LlmPrice(new BigDecimal(input), new BigDecimal(output), new BigDecimal(write5m),
				new BigDecimal(write1h), new BigDecimal(read));
	}

}
