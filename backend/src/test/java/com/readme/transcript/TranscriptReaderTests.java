package com.readme.transcript;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.readme.transcript.TranscriptReader.TranscriptAnswer;
import com.readme.transcript.TranscriptReader.TranscriptAnswer.Row;
import com.readme.transcript.TranscriptResponse.Course;

/** LLM 의 답을 학점 계산기 문서 4절의 꼴로 맞추는 규칙. */
class TranscriptReaderTests {

	@Test
	void abbreviatedCategoriesAreSpelledOutAndOthersKeptAsWritten() {
		assertThat(Arrays.asList("전필", "전선", "교필", "교선", "일선", " 전공기초 ", "교양", "", null).stream()
			.map(TranscriptReader::category)).containsExactly("전공필수", "전공선택", "교양필수", "교양선택", "일반선택", "전공기초", "교양",
					"", "");
	}

	@Test
	void gradesAreUpperCaseWithSymbols() {
		assertThat(Arrays.asList("A+", "a+", "A", "Ao", "AO", "A0", "b", "b0", "B-", "B －", "C＋", "D−", "F", "p", "np", "S",
				"u", "", null, "PASS", "E")
			.stream()
			.map(TranscriptReader::grade)).containsExactly("A+", "A+", "A0", "A0", "A0", "A0", "B0", "B0", "B-", "B-", "C+",
					"D-", "F", "P", "NP", "S", "U", "", "", "PASS", "E");
	}

	@Test
	void termsAreOneTwoSummerOrWinter() {
		assertThat(Arrays.asList("1", "2", "1학기", "제2학기", "여름", "하계 계절학기", "여름학기", "겨울", "동계계절", "3", "", null).stream()
			.map(TranscriptReader::term)).containsExactly("1", "2", "1", "2", "여름", "여름", "여름", "겨울", "겨울", "3", "", "");
	}

	@Test
	void creditsAreNumbersWithoutTrailingZeros() {
		assertThat(TranscriptReader.credit("3")).isEqualTo(new BigDecimal("3"));
		assertThat(TranscriptReader.credit("3.0").toString()).isEqualTo("3");
		assertThat(TranscriptReader.credit("30").toString()).isEqualTo("30");
		assertThat(TranscriptReader.credit("1.50").toString()).isEqualTo("1.5");
		assertThat(TranscriptReader.credit("2학점").toString()).isEqualTo("2");
		assertThat(TranscriptReader.credit("")).isNull();
		assertThat(TranscriptReader.credit("?")).isNull();
		assertThat(TranscriptReader.credit(null)).isNull();
	}

	@Test
	void writtenGradeYearsAreKept() {
		TranscriptResponse response = normalize(row("1", "2019", "1"), row("2학년", "2020", "2"));

		assertThat(response.courses()).extracting(Course::year).containsExactly(1, 2);
	}

	@Test
	void yearsWithoutGradeYearCountFromTheEarliestCalendarYear() {
		// 2019 년이 1학년, 2021 년이 3학년(휴학으로 건너뛴 해도 센다). 학년도 연도도 없는 줄은 앞 줄의 학년이다
		TranscriptResponse response = normalize(row("", "2019학년도", "1"), row("", "2019", "겨울"), row("", "2021", "1"),
				row("", "", "1"));

		assertThat(response.courses()).extracting(Course::year).containsExactly(1, 1, 3, 3);
	}

	@Test
	void calendarYearsInTheGradeYearAndTwoDigitYearsAreCountedAsYears() {
		// 학년 칸의 2019 는 연도다. 두 자리 19 는 2019 다. 1~6 밖의 학년(0, 9)은 없는 것으로 보고 앞 줄의 학년이다
		TranscriptResponse response = normalize(row("2019", "", "1"), row("", "19", "2"), row("", "2020", "1"),
				row("9", "", "2"), row("0", "", "1"), row("2학년", "2019", "1"));

		assertThat(response.courses()).extracting(Course::year).containsExactly(1, 1, 2, 2, 2, 2);
	}

	@Test
	void retakeIsBeforeAfterOrNull() {
		TranscriptResponse response = normalize(retake("before"), retake("AFTER"), retake(""), retake("재수강"), retake(null));

		assertThat(response.courses()).extracting(Course::retake).containsExactly("before", "after", null, null, null);
	}

	@Test
	void unreadableCreditAndUnknownGradeAreUnsure() {
		TranscriptResponse response = normalize(
				new Row("1", "", "1", "전필", "자료구조", "3", "A", "", List.of()),
				new Row("1", "", "1", "전선", "운영체제", "", "B+", "", List.of()),
				new Row("1", "", "1", "교선", "글쓰기", "2", "", "", List.of()),
				new Row("1", "", "1", "교선", "체육", "1", "PASS", "", List.of("grade", "name")),
				new Row("1", "", "1", "교선", "음악", "2", "A+", "", List.of("grade", "credit")),
				// 칸이 밀려 점수가 학점으로 왔다. 10학점까지는 믿는다
				new Row("1", "", "1", "전선", "캡스톤", "95", "A+", "", List.of()),
				new Row("1", "", "1", "전선", "현장실습", "10", "P", "", List.of()));

		assertThat(response.courses()).extracting(Course::credit)
			.containsExactly(new BigDecimal("3"), BigDecimal.ZERO, new BigDecimal("2"), new BigDecimal("1"),
					new BigDecimal("2"), new BigDecimal("95"), new BigDecimal("10"));
		assertThat(response.courses()).extracting(Course::grade).containsExactly("A0", "B+", "", "PASS", "A+", "A+", "P");
		assertThat(response.courses()).extracting(Course::unsure)
			.containsExactly(List.of(), List.of("credit"), List.of("grade"), List.of("grade"), List.of("credit", "grade"),
					List.of("credit"), List.of());
	}

	@Test
	void schoolDepartmentAndScale() {
		TranscriptResponse read = TranscriptReader.normalize(
				new TranscriptAnswer(" 예시대학교 ", "컴퓨터공학과", "4.5", List.of(row("1", "", "1"))));
		assertThat(read.school()).isEqualTo("예시대학교");
		assertThat(read.department()).isEqualTo("컴퓨터공학과");
		assertThat(read.scale()).isEqualTo(4.5);

		assertThat(TranscriptReader.normalize(new TranscriptAnswer("", null, "4.30", List.of())).scale()).isEqualTo(4.3);
		for (String scale : new String[] { "", "4.0", "100", "만점" }) {
			TranscriptResponse other = TranscriptReader.normalize(new TranscriptAnswer("", null, scale, List.of()));
			assertThat(other.scale()).as(scale).isNull();
			assertThat(other.school()).isNull();
			assertThat(other.department()).isNull();
		}
	}

	@Test
	void rowsWithoutANameAreDropped() {
		TranscriptResponse response = TranscriptReader.normalize(new TranscriptAnswer("", "", "",
				List.of(new Row("1", "", "1", "", " ", "3", "A+", "", List.of()), row("1", "", "2"))));

		assertThat(response.courses()).extracting(Course::term).containsExactly("2");
		assertThat(TranscriptReader.normalize(new TranscriptAnswer("", "", "", null)).courses()).isEmpty();
	}

	private static TranscriptResponse normalize(Row... rows) {
		return TranscriptReader.normalize(new TranscriptAnswer("", "", "", List.of(rows)));
	}

	private static Row row(String year, String calendarYear, String term) {
		return new Row(year, calendarYear, term, "전공필수", "과목", "3", "A+", "", List.of());
	}

	private static Row retake(String retake) {
		return new Row("1", "", "1", "전공필수", "과목", "3", "C+", retake, List.of());
	}

}
