package com.readme.profile;

import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.readme.profile.CareerMonths.Span;

import static org.assertj.core.api.Assertions.assertThat;

class CareerMonthsTests {

	private static final YearMonth NOW = YearMonth.of(2026, 11);

	@Test
	void countsBothEndMonths() {
		assertThat(count(span("2023-03", "2024-02"))).isEqualTo(12);
		assertThat(count(span("2026-11", "2026-11"))).isEqualTo(1);
	}

	@Test
	void overlappingMonthsCountOnce() {
		assertThat(count(span("2020-01", "2020-12"), span("2020-07", "2021-06"))).isEqualTo(18);
		// 안에 든 기간과 끝 달만 맞닿은 기간
		assertThat(count(span("2020-01", "2020-12"), span("2020-03", "2020-05"))).isEqualTo(12);
		assertThat(count(span("2020-01", "2020-06"), span("2020-06", "2020-07"))).isEqualTo(7);
	}

	@Test
	void gapsAreNotCounted() {
		assertThat(count(span("2020-01", "2020-06"), span("2021-01", "2021-06"))).isEqualTo(12);
	}

	@Test
	void orderDoesNotMatter() {
		assertThat(count(span("2021-01", "2021-06"), span("2020-01", "2021-02"))).isEqualTo(18);
	}

	@Test
	void currentJobRunsToThisMonth() {
		assertThat(count(span("2025-12", null))).isEqualTo(12);
	}

	@Test
	void monthsAfterThisMonthAreNotCounted() {
		assertThat(count(span("2026-10", "2027-03"))).isEqualTo(2);
		assertThat(count(span("2027-01", null))).isZero();
		assertThat(count()).isZero();
	}

	private static int count(Span... spans) {
		return CareerMonths.count(List.of(spans), NOW);
	}

	private static Span span(String start, String end) {
		return new Span(YearMonth.parse(start), (end != null) ? YearMonth.parse(end) : null);
	}

}
