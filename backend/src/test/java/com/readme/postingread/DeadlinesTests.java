package com.readme.postingread;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.readme.posting.DeadlineKind;
import com.readme.postingread.Deadlines.Read;

/**
 * 마감 읽기. jobfit 의 src/jobfit/dates.py(parse_deadline)를 옮겼다. jobfit 에는 이것만 보는 pytest 가 없어서, dates.py 를
 * python -I 로 직접 돌려 얻은 값을 넣었다.
 *
 * <p>jobfit 의 결과는 (날짜, 날짜가 아닌 원문, 오류) 셋이고 리드미는 마감 종류(date, always, unknown) 하나다.
 * 날짜면 date, jobfit 이 "날짜가 아닌 원문"으로 둔 것 가운데 상시·수시·채용 시 마감은 always, 나머지(미정, 협의, 오류)는 unknown 이다.
 */
class DeadlinesTests {

	/** 통합 테스트의 멈춘 시계와 같은 날. */
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

	// ── dates.py 를 직접 돌려 얻은 값 ─────────────────────────────────────

	@ParameterizedTest(name = "\"{0}\" → {1}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
			2026-11-20                | 2026-11-20
			2026.11.20                | 2026-11-20
			2026/11/20                | 2026-11-20
			2026 . 11 . 20            | 2026-11-20
			2026년 11월 20일           | 2026-11-20
			2026년11월20일(목)         | 2026-11-20
			2026-11-20 23:59          | 2026-11-20
			2026-1-5                  | 2026-01-05
			"　2026-11-20"        | 2026-11-20
			２０２６-１１-２０          | 2026-11-20
			""")
	void datesAreTheSameAsJobfit(String value, LocalDate date) {
		assertThat(Deadlines.parse(value, TODAY)).isEqualTo(new Read(DeadlineKind.DATE, date));
	}

	/** jobfit 이 "날짜가 아닌 원문"으로 두는 것. 상시·수시·채용 시 마감은 날짜 없이 열려 있는 공고라 always 다. */
	@ParameterizedTest(name = "\"{0}\" → {1}")
	@CsvSource(delimiter = '|', textBlock = """
			상시채용      | ALWAYS
			상시         | ALWAYS
			채용시 마감   | ALWAYS
			채용 시 마감  | ALWAYS
			수시채용      | ALWAYS
			미정         | UNKNOWN
			연봉 협의     | UNKNOWN
			""")
	void notADateInJobfit(String value, DeadlineKind kind) {
		assertThat(Deadlines.parse(value, TODAY).kind()).isEqualTo(kind);
	}

	/** jobfit 이 오류로 돌려주는 것. 리드미는 읽지 못한 것(unknown)으로 본다. */
	@ParameterizedTest(name = "\"{0}\"")
	@CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
			2026.02.30
			26.11.20
			마감
			"   "
			""
			""")
	void errorsInJobfitAreUnknown(String value) {
		assertThat(Deadlines.parse(value, TODAY)).isEqualTo(Read.UNKNOWN);
	}

	// ── jobfit 과 다른 점: 연도 없는 날짜, 전각 구분 기호 ────────────────────────────

	/**
	 * 연도 없는 날짜는 오늘에서 앞뒤 6개월 안의 해로 본다. jobfit 은 모두 오류다.
	 * 해가 바뀌는 때: 12월에 읽은 "1/5"는 다음 해, 1월에 읽은 "12/28"은 지난해(이미 마감)다.
	 */
	@ParameterizedTest(name = "오늘 {1}에 \"{0}\" → {2}")
	@CsvSource(delimiter = '|', textBlock = """
			~11/20(목) 23:59 | 2026-11-13 | 2026-11-20
			11/20(목) 23:59  | 2026-11-13 | 2026-11-20
			11월 20일        | 2026-11-13 | 2026-11-20
			11.20           | 2026-11-13 | 2026-11-20
			11/10           | 2026-11-13 | 2026-11-10
			1/5             | 2026-12-20 | 2027-01-05
			12/31           | 2026-12-20 | 2026-12-31
			12/28           | 2027-01-03 | 2026-12-28
			2/29            | 2027-11-13 | 2028-02-29
			""")
	void monthAndDayTakeTheNearestYear(String value, LocalDate today, LocalDate date) {
		assertThat(Deadlines.parse(value, today)).isEqualTo(new Read(DeadlineKind.DATE, date));
	}

	/** 전각 글자는 NFKC 로 바꿔 읽는다. jobfit 은 전각 숫자만 읽고 전각 마침표·물결표·年月日에서는 오류다. */
	@ParameterizedTest(name = "\"{0}\" → {1}")
	@CsvSource(delimiter = '|', textBlock = """
			２０２６．１１．２０       | 2026-11-20
			～１１／２０              | 2026-11-20
			""")
	void fullwidthFormsAreNormalized(String value, LocalDate date) {
		assertThat(Deadlines.parse(value, TODAY)).isEqualTo(new Read(DeadlineKind.DATE, date));
	}

	@ParameterizedTest(name = "\"{0}\"")
	@CsvSource(delimiter = '|', textBlock = """
			２０２６年１１月２０日
			13/1
			2/30
			11/20/2026
			""")
	void stillUnreadable(String value) {
		assertThat(Deadlines.parse(value, TODAY)).isEqualTo(Read.UNKNOWN);
	}

}
