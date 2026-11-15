package com.readme.profile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.readme.keyword.KeywordDictionary;
import com.readme.profile.Profile.Career;
import com.readme.profile.Profile.Certificate;
import com.readme.profile.Profile.Education;
import com.readme.profile.Profile.Language;
import com.readme.profile.Profile.ProfileInput;

/**
 * 다른 기능이 프로필의 빈 칸을 채울 때 넘기는 값(가입 값, 자료에서 뽑은 값). {@link Profiles#fillBlanks} 가 받는다.
 * 사용자가 친 값이 아니라서 틀린 값은 오류를 내지 않고 버린다(cleaned).
 *
 * 년월 칸은 "YYYY-MM" 이고, 그 꼴이 아니면 버린다.
 *
 * @param gender "MALE", "FEMALE". 그 밖의 값은 버린다
 */
public record ProfileDraft(String name, LocalDate birthDate, String gender, List<EducationDraft> educations,
		List<CertificateDraft> certificates, List<LanguageDraft> languages, List<CareerDraft> careers) {

	private static final Pattern MONTH = Pattern.compile("[0-9]{4}-(0[1-9]|1[0-2])");

	private static final LocalDate OLDEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

	/** 이름, 생년월일, 성별만 있는 것(가입 값). */
	public static ProfileDraft basics(String name, LocalDate birthDate, String gender) {
		return new ProfileDraft(name, birthDate, gender, List.of(), List.of(), List.of(), List.of());
	}

	public record EducationDraft(String school, String major, String graduation, BigDecimal gpa, BigDecimal gpaScale) {
	}

	public record CertificateDraft(String name, String acquired) {
	}

	public record LanguageDraft(String test, String score, String taken) {
	}

	/** @param end 없으면 재직 중 */
	public record CareerDraft(String company, String start, String end, String role) {
	}

	/**
	 * 테이블에 넣을 수 있는 값으로 다듬는다. 꼭 있어야 하는 칸이 빈 줄과 틀린 년월·성별·생년월일은 버리고, 긴 글은 칸 길이로 자른다.
	 * 학점이 만점보다 크면 둘 다 버린다. 경력의 끝이 시작보다 앞이면 끝을 버린다(재직 중이 된다). 여러 줄은 20줄까지다.
	 */
	ProfileInput cleaned(LocalDate today) {
		KeywordDictionary certificateNames = KeywordDictionary.certificates();
		return new ProfileInput(cut(this.name, 50),
				(this.birthDate != null && !this.birthDate.isAfter(today) && !this.birthDate.isBefore(OLDEST_BIRTH_DATE))
						? this.birthDate : null,
				("MALE".equals(this.gender) || "FEMALE".equals(this.gender)) ? this.gender : null,
				rows(this.educations).filter((e) -> cut(e.school(), 100) != null).map(ProfileDraft::education).limit(20).toList(),
				rows(this.certificates).filter((c) -> cut(c.name(), 100) != null)
					.map((c) -> new Certificate(cut(certificateNames.normalize(c.name()), 100), month(c.acquired())))
					.limit(20)
					.toList(),
				rows(this.languages).filter((l) -> cut(l.test(), 50) != null && cut(l.score(), 20) != null)
					.map((l) -> new Language(cut(l.test(), 50), cut(l.score(), 20), month(l.taken())))
					.limit(20)
					.toList(),
				rows(this.careers).filter((c) -> cut(c.company(), 100) != null && month(c.start()) != null)
					.map(ProfileDraft::career)
					.limit(20)
					.toList());
	}

	private static Education education(EducationDraft draft) {
		BigDecimal gpa = draft.gpa();
		BigDecimal scale = draft.gpaScale();
		boolean fits = gpa == null || scale == null || (gpa.signum() >= 0 && scale.signum() > 0 && gpa.compareTo(scale) <= 0
				&& gpa.compareTo(BigDecimal.valueOf(1000)) < 0 && scale.compareTo(BigDecimal.valueOf(1000)) < 0);
		return new Education(cut(draft.school(), 100), cut(draft.major(), 100), month(draft.graduation()),
				fits ? scaled(gpa) : null, fits ? scaled(scale) : null);
	}

	private static Career career(CareerDraft draft) {
		String start = month(draft.start());
		String end = month(draft.end());
		return new Career(cut(draft.company(), 100), start, (end != null && end.compareTo(start) < 0) ? null : end,
				cut(draft.role(), 100));
	}

	private static BigDecimal scaled(BigDecimal value) {
		return (value != null) ? value.setScale(2, RoundingMode.HALF_UP) : null;
	}

	private static <T> Stream<T> rows(List<T> values) {
		return (values != null) ? values.stream().filter(Objects::nonNull) : Stream.empty();
	}

	private static String month(String value) {
		return (value != null && MONTH.matcher(value.strip()).matches()) ? value.strip() : null;
	}

	/** 앞뒤 공백을 지우고 max 자(UTF-16)까지. 비면 null. */
	private static String cut(String value, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String text = value.strip();
		return (text.length() <= max) ? text : text.substring(0, max).strip();
	}

}
