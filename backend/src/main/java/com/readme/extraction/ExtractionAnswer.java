package com.readme.extraction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.readme.profile.ProfileDraft;
import com.readme.profile.ProfileDraft.CareerDraft;
import com.readme.profile.ProfileDraft.CertificateDraft;
import com.readme.profile.ProfileDraft.EducationDraft;
import com.readme.profile.ProfileDraft.LanguageDraft;

/**
 * 자료 조각 하나에서 LLM 이 뽑은 것(구조화 출력의 모양, 프롬프트 exp-extract.md): 카드와 프로필 칸. 모든 칸이 늘 오고, 자료에 없으면
 * "" 나 [] 다. 날짜와 학점도 글자로 받는다(스키마를 단순하게 두고, 틀린 값은 여기서 버린다).
 */
record ExtractionAnswer(List<ExtractedCard> cards, ProfileAnswer profile) {

	/** @param gender "MALE", "FEMALE", "" */
	record ProfileAnswer(String name, String birthDate, String gender, List<Education> educations,
			List<Certificate> certificates, List<Language> languages, List<Career> careers) {
	}

	record Education(String school, String major, String graduation, String gpa, String gpaScale) {
	}

	record Certificate(String name, String acquired) {
	}

	record Language(String test, String score, String taken) {
	}

	record Career(String company, String start, String end, String role) {
	}

	/** 프로필의 빈 칸에 넣을 값. 틀린 값은 Profiles.fillBlanks 가 버린다. 프로필 칸이 없으면 null. */
	ProfileDraft profileDraft() {
		ProfileAnswer p = this.profile;
		if (p == null) {
			return null;
		}
		return new ProfileDraft(p.name(), date(p.birthDate()), p.gender(),
				orEmpty(p.educations()).stream()
					.map((e) -> new EducationDraft(e.school(), e.major(), e.graduation(), number(e.gpa()), number(e.gpaScale())))
					.toList(),
				orEmpty(p.certificates()).stream().map((c) -> new CertificateDraft(c.name(), c.acquired())).toList(),
				orEmpty(p.languages()).stream().map((l) -> new LanguageDraft(l.test(), l.score(), l.taken())).toList(),
				orEmpty(p.careers()).stream()
					.map((c) -> new CareerDraft(c.company(), c.start(), c.end(), c.role()))
					.toList());
	}

	private static LocalDate date(String value) {
		try {
			return (value == null || value.isBlank()) ? null : LocalDate.parse(value.strip());
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	private static BigDecimal number(String value) {
		try {
			return (value == null || value.isBlank()) ? null : new BigDecimal(value.strip());
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static <T> List<T> orEmpty(List<T> values) {
		return (values != null) ? values : List.of();
	}

}
