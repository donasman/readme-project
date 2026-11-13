package com.readme.posting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 상태 글자(설계 5.3).
 */
class ApplicationStatusTests {

	@ParameterizedTest(name = "{0} {1} {2} → {3}")
	@CsvSource(nullValues = "-", value = {
			// 지원함이 아니면 글자가 없다
			"UNDECIDED, -,           -,           -",
			"HOLD,      -,           -,           -",
			"SKIP,      -,           -,           -",
			// 서류 진행 중은 그냥 지원함
			"APPLIED,   DOCUMENT,    IN_PROGRESS, 지원함",
			// 다른 단계 진행 중은 "단계"
			"APPLIED,   TEST,        IN_PROGRESS, 필기 단계",
			"APPLIED,   INTERVIEW_1, IN_PROGRESS, 1차 면접 단계",
			"APPLIED,   INTERVIEW_2, IN_PROGRESS, 2차 면접 단계",
			"APPLIED,   FINAL,       IN_PROGRESS, 최종 단계",
			// 결과가 나오면 어느 단계든 합격·불합격
			"APPLIED,   DOCUMENT,    PASSED,      서류 합격",
			"APPLIED,   TEST,        FAILED,      필기 불합격",
			"APPLIED,   INTERVIEW_1, PASSED,      1차 면접 합격",
			"APPLIED,   FINAL,       PASSED,      최종 합격",
			"APPLIED,   FINAL,       FAILED,      최종 불합격" })
	void label(String decision, String stage, String result, String expected) {
		assertThat(ApplicationStatus.of(decision, stage, result).label()).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0} {1} {2} → {3}")
	@CsvSource(nullValues = "-", value = {
			"UNDECIDED, -,           -,           -",
			// 그냥 지원함은 지원 표시로 충분하므로 더 보일 글자가 없다
			"APPLIED,   DOCUMENT,    IN_PROGRESS, -",
			"APPLIED,   DOCUMENT,    PASSED,      서류 합격",
			"APPLIED,   DOCUMENT,    FAILED,      서류 불합격",
			"APPLIED,   INTERVIEW_1, IN_PROGRESS, 1차 면접 단계" })
	void progressLabel(String decision, String stage, String result, String expected) {
		assertThat(ApplicationStatus.of(decision, stage, result).progressLabel()).isEqualTo(expected);
	}

	@Test
	void noRowMeansNoStatus() {
		assertThat(ApplicationStatus.of(null, null, null)).isNull();
		assertThat(ApplicationStatus.labelOf(null)).isNull();
	}

}
