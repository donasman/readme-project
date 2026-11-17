package com.readme.profile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.readme.profile.CareerMonths.Span;
import com.readme.profile.ProfileResponse.CareerResponse;
import com.readme.profile.ProfileResponse.CertificateResponse;
import com.readme.profile.ProfileResponse.EducationResponse;
import com.readme.profile.ProfileResponse.LanguageResponse;

@Component
class ProfileQueries {

	private final JdbcClient jdbc;

	private final Clock clock;

	ProfileQueries(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	private record Basics(String name, LocalDate birthDate) {
	}

	/** 내 프로필. 저장한 적이 없으면 빈 프로필이다. */
	ProfileResponse find(long userId) {
		Basics basics = this.jdbc.sql("select name, birth_date from profiles where user_id = :userId")
			.param("userId", userId)
			.query((rs, rowNum) -> new Basics(rs.getString("name"), rs.getObject("birth_date", LocalDate.class)))
			.optional()
			.orElse(new Basics(null, null));
		List<EducationResponse> educations = this.jdbc
			.sql("select school, major, graduation, gpa, gpa_scale from profile_educations where user_id = :userId order by seq")
			.param("userId", userId)
			.query((rs, rowNum) -> new EducationResponse(rs.getString("school"), rs.getString("major"),
					rs.getString("graduation"), rs.getBigDecimal("gpa"), rs.getBigDecimal("gpa_scale")))
			.list();
		List<CertificateResponse> certificates = this.jdbc
			.sql("select name, acquired from profile_certificates where user_id = :userId order by seq")
			.param("userId", userId)
			.query((rs, rowNum) -> new CertificateResponse(rs.getString("name"), rs.getString("acquired")))
			.list();
		List<LanguageResponse> languages = this.jdbc
			.sql("select test, score, taken from profile_languages where user_id = :userId order by seq")
			.param("userId", userId)
			.query((rs, rowNum) -> new LanguageResponse(rs.getString("test"), rs.getString("score"), rs.getString("taken")))
			.list();
		List<CareerResponse> careers = this.jdbc
			.sql("select company, start_month, end_month, role from profile_careers where user_id = :userId order by seq")
			.param("userId", userId)
			.query((rs, rowNum) -> new CareerResponse(rs.getString("company"), rs.getString("start_month"),
					rs.getString("end_month"), rs.getString("role")))
			.list();
		int careerMonths = CareerMonths.count(careers.stream()
			.map((c) -> new Span(YearMonth.parse(c.start()), (c.end() != null) ? YearMonth.parse(c.end()) : null))
			.toList(), YearMonth.now(this.clock));
		return new ProfileResponse(basics.name(), basics.birthDate(), educations, certificates, languages, careers,
				careerMonths, careerMonths / 12);
	}

}
