package com.readme.grouppost;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.readme.group.Disclosure;
import com.readme.group.MemberResponse;
import com.readme.posting.ApplicationStatus;

/**
 * 관심 멤버에게 실리는 지원 표시는 그 멤버의 공개 범위만큼이다(설계 5.2).
 */
class InterestResponseTests {

	private static final MemberResponse MEMBER = new MemberResponse(7, "나래");

	@ParameterizedTest(name = "{0}, {1} {2} {3} → applied={4}, progress={5}")
	@CsvSource(nullValues = "-", value = {
			// 관심만: 지원했어도 표시가 없다
			"INTEREST, APPLIED,   INTERVIEW_1, IN_PROGRESS, false, -",
			"INTEREST, -,         -,           -,           false, -",
			// 지원 여부까지: 지원함만 보이고 단계는 안 보인다
			"APPLIED,  APPLIED,   INTERVIEW_1, IN_PROGRESS, true,  -",
			"APPLIED,  APPLIED,   TEST,        FAILED,      true,  -",
			"APPLIED,  UNDECIDED, -,           -,           false, -",
			"APPLIED,  -,         -,           -,           false, -",
			// 단계·결과까지: 그냥 지원함이 아닐 때 그 글자가 붙는다
			"PROGRESS, APPLIED,   INTERVIEW_1, IN_PROGRESS, true,  1차 면접 단계",
			"PROGRESS, APPLIED,   TEST,        FAILED,      true,  필기 불합격",
			"PROGRESS, APPLIED,   DOCUMENT,    IN_PROGRESS, true,  -",
			"PROGRESS, SKIP,      -,           -,           false, -",
			"PROGRESS, -,         -,           -,           false, -" })
	void cutToTheMembersDisclosure(Disclosure disclosure, String decision, String stage, String result,
			boolean applied, String progress) {
		InterestResponse interest = InterestResponse.of(MEMBER, disclosure, ApplicationStatus.of(decision, stage, result));

		assertThat(interest.member()).isEqualTo(MEMBER);
		assertThat(interest.applied()).isEqualTo(applied);
		assertThat(interest.progress()).isEqualTo(progress);
	}

}
