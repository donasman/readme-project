package com.readme.postingread;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.readme.posting.DeadlineKind;

/**
 * 마감 읽기. jobfit 의 src/jobfit/dates.py(parse_deadline)를 옮겼다. 입력은 요약 칸의 값("2026.10.27 23:59", "상시채용")이다.
 *
 * <p>jobfit 과 다른 점(DeadlinesTests):
 * <ul>
 * <li>결과는 마감 종류 하나다. 상시·수시·채용 시 마감은 always, 미정·협의와 읽지 못한 것은 unknown.</li>
 * <li>"2026.11.01 ~ 2026.11.20"처럼 기간이면 물결표 뒤를 읽는다. jobfit 은 이것을 부르는 meta.py 가 한다.</li>
 * <li>NFKC 로 전각 글자를 바꾼 뒤 읽는다(２０２６．１１．２０, ～). jobfit 은 전각 숫자만 읽는다.</li>
 * <li>연도 없는 날짜("~11/20(목) 23:59", "11월 20일")를 오늘에서 앞뒤 6개월 안의 해로 읽는다. jobfit 은 오류다.
 * 오늘은 서울 날짜로, 부르는 쪽이 주입받은 Clock 으로 구해 넘긴다.</li>
 * </ul>
 */
final class Deadlines {

	private static final int U = Pattern.UNICODE_CHARACTER_CLASS;

	private static final List<Pattern> DATES = List.of(
			Pattern.compile("^(\\d{4})\\s*[-./]\\s*(\\d{1,2})\\s*[-./]\\s*(\\d{1,2})", U),
			Pattern.compile("^(\\d{4})\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일?", U));

	/** 연도 없는 날짜. 뒤에 숫자가 더 붙으면("26.11.20", "11/20/2026") 어느 칸이 연도인지 몰라 읽지 않는다. */
	private static final List<Pattern> MONTH_DAYS = List.of(
			Pattern.compile("^(\\d{1,2})\\s*[-./]\\s*(\\d{1,2})(?!\\d|\\s*[-./]\\s*\\d)", U),
			Pattern.compile("^(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일?", U));

	/** jobfit 의 _NO_DEADLINE(상시|채용\s*시|수시|미정|협의) 가운데 날짜 없이 열려 있다는 뜻인 것. */
	private static final Pattern ALWAYS = Pattern.compile("상시|채용\\s*시|수시", U);

	private static final Pattern RANGE = Pattern.compile("[~〜]");

	private static final Pattern WS = Pattern.compile("[\\s\\u3000\\u00a0]+", U);

	private Deadlines() {
	}

	/** 읽은 마감. date 는 kind 가 DATE 일 때만 있다. */
	record Read(DeadlineKind kind, LocalDate date) {

		static final Read UNKNOWN = new Read(DeadlineKind.UNKNOWN, null);

		static final Read ALWAYS = new Read(DeadlineKind.ALWAYS, null);

		boolean known() {
			return this.kind != DeadlineKind.UNKNOWN;
		}

	}

	static Read parse(String value, LocalDate today) {
		if (value == null) {
			return Read.UNKNOWN;
		}
		String s = Normalizer.normalize(value, Normalizer.Form.NFKC);
		String[] range = RANGE.split(s, -1);
		s = Py.strip(WS.matcher(range[range.length - 1]).replaceAll(" "));
		for (Pattern pattern : DATES) {
			Matcher m = pattern.matcher(s);
			if (m.find()) {
				try {
					return date(LocalDate.of(number(m, 1), number(m, 2), number(m, 3)));
				}
				catch (DateTimeException ex) {
					return Read.UNKNOWN; // 없는 날짜(2026.02.30)
				}
			}
		}
		for (Pattern pattern : MONTH_DAYS) {
			Matcher m = pattern.matcher(s);
			if (m.find()) {
				try {
					return date(nearestYear(MonthDay.of(number(m, 1), number(m, 2)), today));
				}
				catch (DateTimeException ex) {
					return Read.UNKNOWN;
				}
			}
		}
		return ALWAYS.matcher(s).find() ? Read.ALWAYS : Read.UNKNOWN;
	}

	/** 오늘에서 앞뒤 6개월 안의 해. 공고의 마감은 대개 몇 주 안이라, 12월에 읽은 "1/5"는 다음 해다. */
	private static LocalDate nearestYear(MonthDay monthDay, LocalDate today) {
		LocalDate date = monthDay.atYear(today.getYear());
		if (date.isBefore(today.minusMonths(6))) {
			return monthDay.atYear(today.getYear() + 1);
		}
		if (date.isAfter(today.plusMonths(6))) {
			return monthDay.atYear(today.getYear() - 1);
		}
		return date;
	}

	private static Read date(LocalDate date) {
		return new Read(DeadlineKind.DATE, date);
	}

	private static int number(Matcher m, int group) {
		return Integer.parseInt(m.group(group));
	}

}
