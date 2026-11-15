package com.readme.profile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.readme.Fixtures;
import com.readme.IntegrationTest;
import com.readme.profile.ProfileDraft.CareerDraft;
import com.readme.profile.ProfileDraft.CertificateDraft;
import com.readme.profile.ProfileDraft.EducationDraft;
import com.readme.profile.ProfileDraft.LanguageDraft;
import com.readme.profile.ProfileResponse.CareerResponse;
import com.readme.profile.ProfileResponse.CertificateResponse;
import com.readme.profile.ProfileResponse.EducationResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** 다른 기능이 부르는 빈 칸 채우기(Profiles.fillBlanks). 오늘은 2026-11-13 이다. */
@IntegrationTest
class ProfileFillTests {

	@Autowired
	Profiles profiles;

	@Autowired
	ProfileQueries queries;

	@Autowired
	Fixtures fixtures;

	@Test
	void fillsAnEmptyProfile() {
		long me = this.fixtures.user("다온");

		this.profiles.fillBlanks(me, new ProfileDraft(" 김다온 ", LocalDate.of(1999, 3, 1), "FEMALE",
				List.of(new EducationDraft("한국대학교", "컴퓨터공학", "2024-02", new BigDecimal("3.8"), new BigDecimal("4.5"))),
				List.of(new CertificateDraft("컴활", "2022-08")), List.of(new LanguageDraft("TOEIC", "900", "2025-01")),
				List.of(new CareerDraft("가나다", "2024-03", null, "백엔드 개발"))));

		ProfileResponse profile = this.queries.find(me);
		assertThat(profile.name()).isEqualTo("김다온");
		assertThat(profile.birthDate()).isEqualTo(LocalDate.of(1999, 3, 1));
		assertThat(profile.gender()).isEqualTo("female");
		assertThat(profile.educations()).containsExactly(
				new EducationResponse("한국대학교", "컴퓨터공학", "2024-02", new BigDecimal("3.80"), new BigDecimal("4.50")));
		assertThat(profile.certificates()).containsExactly(new CertificateResponse("컴퓨터활용능력", "2022-08"));
		assertThat(profile.careers()).containsExactly(new CareerResponse("가나다", "2024-03", null, "백엔드 개발"));
	}

	@Test
	void neverOverwritesWhatIsThere() {
		long me = this.fixtures.user("다온");
		this.profiles.fillBlanks(me, new ProfileDraft("김다온", null, null, List.of(), List.of(new CertificateDraft("SQLD", null)),
				List.of(), List.of()));

		this.profiles.fillBlanks(me, new ProfileDraft("다른 이름", LocalDate.of(1999, 3, 1), "MALE", List.of(),
				List.of(new CertificateDraft("ADsP", null)), List.of(new LanguageDraft("OPIc", "IH", null)), List.of()));

		ProfileResponse profile = this.queries.find(me);
		assertThat(profile.name()).isEqualTo("김다온");
		assertThat(profile.birthDate()).isEqualTo(LocalDate.of(1999, 3, 1));
		assertThat(profile.gender()).isEqualTo("male");
		assertThat(profile.certificates()).extracting(CertificateResponse::name).containsExactly("SQLD");
		assertThat(profile.languages()).hasSize(1);
	}

	@Test
	void dropsValuesThatDoNotFit() {
		long me = this.fixtures.user("다온");

		this.profiles.fillBlanks(me, new ProfileDraft("   ", LocalDate.of(2026, 11, 14), "OTHER",
				List.of(new EducationDraft("한국대학교", null, "2024년 2월", new BigDecimal("4.6"), new BigDecimal("4.5")),
						new EducationDraft(" ", "전공만", null, null, null)),
				List.of(new CertificateDraft("가".repeat(150), "2022")), List.of(new LanguageDraft("TOEIC", "", null)),
				List.of(new CareerDraft("가나다", "2025-03", "2025-02", null), new CareerDraft("라마바", "2024년", null, null))));

		ProfileResponse profile = this.queries.find(me);
		assertThat(profile.name()).isNull();
		assertThat(profile.birthDate()).isNull();
		assertThat(profile.gender()).isNull();
		assertThat(profile.educations()).containsExactly(new EducationResponse("한국대학교", null, null, null, null));
		assertThat(profile.certificates()).containsExactly(new CertificateResponse("가".repeat(100), null));
		assertThat(profile.languages()).isEmpty();
		assertThat(profile.careers()).containsExactly(new CareerResponse("가나다", "2025-03", null, null));
	}

}
