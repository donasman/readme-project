package com.readme.profile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GET·PUT /api/me/profile 의 응답(설계 4절). 프로필을 저장한 적이 없으면 빈 칸과 빈 목록이다.
 *
 * @param gender       "male", "female", null
 * @param careerMonths 경력 칸으로 센 개월 수(겹친 달은 한 번, 재직 중은 이번 달까지. CareerMonths)
 * @param careerYears  careerMonths ÷ 12 버림
 */
record ProfileResponse(String name, LocalDate birthDate, String gender, List<EducationResponse> educations,
		List<CertificateResponse> certificates, List<LanguageResponse> languages, List<CareerResponse> careers,
		int careerMonths, int careerYears) {

	record EducationResponse(String school, String major, String graduation, BigDecimal gpa, BigDecimal gpaScale) {
	}

	record CertificateResponse(String name, String acquired) {
	}

	record LanguageResponse(String test, String score, String taken) {
	}

	/** @param end null 이면 재직 중 */
	record CareerResponse(String company, String start, String end, String role) {
	}

}
