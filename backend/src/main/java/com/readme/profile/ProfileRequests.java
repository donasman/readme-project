package com.readme.profile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;
import com.readme.keyword.KeywordDictionary;
import com.readme.profile.Profile.Career;
import com.readme.profile.Profile.Certificate;
import com.readme.profile.Profile.Education;
import com.readme.profile.Profile.Language;
import com.readme.profile.Profile.ProfileInput;

/**
 * PUT /api/me/profile 의 본문(설계 4절). 모든 칸을 보낸 값으로 바꾼다. 길이는 테이블 칸과 같고, 여러 줄은 각각 20줄까지다.
 * 년월은 "YYYY-MM", 빈 글자는 넣지 않은 것이다.
 */
final class ProfileRequests {

	/** 년월. 빈 글자도 받는다(넣지 않은 것). */
	private static final String MONTH = "^$|^[0-9]{4}-(0[1-9]|1[0-2])$";

	private static final LocalDate OLDEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

	private ProfileRequests() {
	}

	record ProfileRequest(@Size(max = 50) String name, LocalDate birthDate,
			@Size(max = 20) List<@NotNull @Valid EducationRequest> educations,
			@Size(max = 20) List<@NotNull @Valid CertificateRequest> certificates,
			@Size(max = 20) List<@NotNull @Valid LanguageRequest> languages,
			@Size(max = 20) List<@NotNull @Valid CareerRequest> careers) {

		/**
		 * 저장할 값. 앞뒤 공백을 지우고 빈 글자는 null 로 본다. 자격증 이름은 자격증 사전의 대표 표기로 바꾼다(컴활 → 컴퓨터활용능력).
		 * 칸끼리 맞지 않으면(학점이 만점보다 크다, 경력의 끝이 시작보다 앞이다, 생년월일이 오늘 뒤다) 400 INVALID_INPUT.
		 */
		ProfileInput toInput(LocalDate today) {
			if (this.birthDate != null && (this.birthDate.isAfter(today) || this.birthDate.isBefore(OLDEST_BIRTH_DATE))) {
				throw invalid("생년월일을 확인해 주세요");
			}
			KeywordDictionary certificateNames = KeywordDictionary.certificates();
			return new ProfileInput(blankToNull(this.name), this.birthDate,
					orEmpty(this.educations).stream().map(EducationRequest::toEducation).toList(),
					orEmpty(this.certificates).stream()
						.map((c) -> new Certificate(certificateNames.normalize(c.name()), blankToNull(c.acquired())))
						.toList(),
					orEmpty(this.languages).stream()
						.map((l) -> new Language(l.test().strip(), l.score().strip(), blankToNull(l.taken())))
						.toList(),
					orEmpty(this.careers).stream().map(CareerRequest::toCareer).toList());
		}

	}

	record EducationRequest(@NotBlank @Size(max = 100) String school, @Size(max = 100) String major,
			@Pattern(regexp = MONTH) String graduation, @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal gpa,
			@DecimalMin(value = "0", inclusive = false) @Digits(integer = 3, fraction = 2) BigDecimal gpaScale) {

		Education toEducation() {
			if (this.gpa != null && this.gpaScale != null && this.gpa.compareTo(this.gpaScale) > 0) {
				throw invalid("학점이 만점보다 큽니다");
			}
			return new Education(this.school.strip(), blankToNull(this.major), blankToNull(this.graduation), this.gpa,
					this.gpaScale);
		}

	}

	record CertificateRequest(@NotBlank @Size(max = 100) String name, @Pattern(regexp = MONTH) String acquired) {
	}

	record LanguageRequest(@NotBlank @Size(max = 50) String test, @NotBlank @Size(max = 20) String score,
			@Pattern(regexp = MONTH) String taken) {
	}

	/** @param end 비어 있으면 재직 중 */
	record CareerRequest(@NotBlank @Size(max = 100) String company, @NotBlank @Pattern(regexp = MONTH) String start,
			@Pattern(regexp = MONTH) String end, @Size(max = 100) String role) {

		Career toCareer() {
			String endMonth = blankToNull(this.end);
			if (endMonth != null && endMonth.compareTo(this.start) < 0) {
				throw invalid("경력의 끝 달이 시작 달보다 앞입니다");
			}
			return new Career(this.company.strip(), this.start, endMonth, blankToNull(this.role));
		}

	}

	private static ApiException invalid(String message) {
		return new ApiException(CommonErrorCode.INVALID_INPUT, message);
	}

	private static <T> List<T> orEmpty(List<T> values) {
		return (values != null) ? values : List.of();
	}

	private static String blankToNull(String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

}
