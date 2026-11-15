package com.readme.profile;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

/** 시계는 2026-11-13(서울)이라 이번 달은 2026-11 이다. */
@IntegrationTest
class ProfileTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Test
	void emptyBeforeSaving() {
		long me = this.fixtures.user("다온");

		assertThat(this.mvc.get().uri("/api/me/profile").with(loginAs(me))).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{ "name": null, "birthDate": null, "gender": null, "educations": [], "certificates": [], "languages": [],
				  "careers": [], "careerMonths": 0, "careerYears": 0 }
				""");
	}

	@Test
	void saveAndReadBack() {
		long me = this.fixtures.user("다온");

		assertThat(put(me, """
				{ "name": " 김다온 ", "birthDate": "1999-03-01", "gender": "female",
				  "educations": [{ "school": "한국대학교", "major": "컴퓨터공학", "graduation": "2024-02", "gpa": 3.8, "gpaScale": 4.5 }],
				  "certificates": [{ "name": "컴활", "acquired": "2022-08" }, { "name": "리눅스 마스터", "acquired": "" },
				                   { "name": "새 자격증" }],
				  "languages": [{ "test": "TOEIC", "score": "900", "taken": "2025-01" }],
				  "careers": [{ "company": "가나다", "start": "2024-03", "end": "2025-02", "role": "백엔드 개발" },
				              { "company": "라마바", "start": "2025-01", "end": null, "role": "" }] }
				"""))
			.hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo("""
					{ "name": "김다온", "birthDate": "1999-03-01", "gender": "female",
					  "educations": [{ "school": "한국대학교", "major": "컴퓨터공학", "graduation": "2024-02", "gpa": 3.80, "gpaScale": 4.50 }],
					  "certificates": [{ "name": "컴퓨터활용능력", "acquired": "2022-08" }, { "name": "리눅스마스터", "acquired": null },
					                   { "name": "새 자격증", "acquired": null }],
					  "languages": [{ "test": "TOEIC", "score": "900", "taken": "2025-01" }],
					  "careers": [{ "company": "가나다", "start": "2024-03", "end": "2025-02", "role": "백엔드 개발" },
					              { "company": "라마바", "start": "2025-01", "end": null, "role": null }],
					  "careerMonths": 33, "careerYears": 2 }
					""");
		assertThat(this.mvc.get().uri("/api/me/profile").with(loginAs(me))).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{ "name": "김다온", "careerMonths": 33 }
					""");
	}

	@Test
	void saveReplacesEveryField() {
		long me = this.fixtures.user("다온");
		put(me, """
				{ "name": "김다온", "gender": "male", "certificates": [{ "name": "SQLD" }, { "name": "ADsP" }] }
				""");

		assertThat(put(me, """
				{ "certificates": [{ "name": "SQLP" }] }
				""")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "name": null, "gender": null, "certificates": [{ "name": "SQLP", "acquired": null }] }
				""");
	}

	@Test
	void onlyMine() {
		long me = this.fixtures.user("다온");
		long other = this.fixtures.user("가람");
		put(me, """
				{ "name": "김다온" }
				""");

		assertThat(this.mvc.get().uri("/api/me/profile").with(loginAs(other))).hasStatusOk()
			.bodyJson()
			.extractingPath("$.name")
			.isNull();
	}

	@Test
	void invalidValuesAre400() {
		long me = this.fixtures.user("다온");

		assertInvalid(me, """
				{ "gender": "other" }
				""", "성별은 male, female 중 하나이거나 비워 주세요");
		assertInvalid(me, """
				{ "birthDate": "2026-11-14" }
				""", "생년월일을 확인해 주세요");
		assertInvalid(me, """
				{ "educations": [{ "school": "한국대학교", "gpa": 4.6, "gpaScale": 4.5 }] }
				""", "학점이 만점보다 큽니다");
		assertInvalid(me, """
				{ "careers": [{ "company": "가나다", "start": "2025-03", "end": "2025-02" }] }
				""", "경력의 끝 달이 시작 달보다 앞입니다");
		assertThat(put(me, """
				{ "careers": [{ "company": "가나다", "start": "2025-13" }] }
				""")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
		assertThat(put(me, """
				{ "certificates": [{ "name": " " }] }
				""")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void needsLogin() {
		assertThat(this.mvc.get().uri("/api/me/profile")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MvcTestResult put(long userId, String json) {
		return this.mvc.put()
			.uri("/api/me/profile")
			.with(loginAs(userId))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.exchange();
	}

	private void assertInvalid(long userId, String json, String message) {
		assertThat(put(userId, json)).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isLenientlyEqualTo("""
				{ "code": "INVALID_INPUT", "message": "%s" }
				""".formatted(message));
	}

}
