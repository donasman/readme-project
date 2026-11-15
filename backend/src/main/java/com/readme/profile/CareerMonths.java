package com.readme.profile;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * 경력 칸으로 센 경력 개월 수(프로필 설계 PR2, 2026-11-16 정함). 연차는 이 값 ÷ 12 를 버린 것이다.
 *
 * <ul>
 * <li>기간은 시작 달과 끝 달을 모두 넣어 센다(2023-03 ~ 2024-02 는 12개월).</li>
 * <li>겹친 달은 한 번만 센다(겸직, 기간을 잘못 넣은 것).</li>
 * <li>끝 달이 없으면(재직 중) 이번 달까지다. 이번 달 뒤의 달은 세지 않는다.</li>
 * </ul>
 */
final class CareerMonths {

	private CareerMonths() {
	}

	/** @param end null 이면 재직 중 */
	record Span(YearMonth start, YearMonth end) {
	}

	static int count(List<Span> spans, YearMonth thisMonth) {
		List<Span> clipped = spans.stream()
			.map((span) -> new Span(span.start(), (span.end() == null || span.end().isAfter(thisMonth)) ? thisMonth
					: span.end()))
			.filter((span) -> !span.start().isAfter(span.end()))
			.sorted(Comparator.comparing(Span::start))
			.toList();
		int months = 0;
		YearMonth counted = null; // 이미 센 마지막 달
		for (Span span : clipped) {
			YearMonth from = (counted == null || span.start().isAfter(counted)) ? span.start() : counted.plusMonths(1);
			if (!from.isAfter(span.end())) {
				months += (int) (from.until(span.end(), ChronoUnit.MONTHS) + 1);
				counted = span.end();
			}
		}
		return months;
	}

}
