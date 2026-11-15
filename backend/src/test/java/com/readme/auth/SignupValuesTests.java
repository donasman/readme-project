package com.readme.auth;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

/**
 * 가입 값(생년월일·성별)과 동의 둘(로그인 설계 4.1, 2026-11-16). 오늘은 서울로 2026-11-13, UTC 로는 11-12 다.
 */
@IntegrationTest
class SignupValuesTests {

	private static final String NEEDS_CONSENT = "생년월일이나 성별을 넣으려면 수집·이용에 동의해 주세요";

	private static final String BAD_BIRTH_DATE = "생년월일을 확인해 주세요";

	private static final String BAD_GENDER = "성별은 male, female 중 하나이거나 비워 주세요";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	@Test
	void signupKeepsTheConsentsAndPutsTheValuesInTheProfile() {
		String email = newEmail();

		assertThat(signup(email, """
				"birthDate": "1999-03-01", "gender": "female", "birthGenderConsent": true, "eventNoticeConsent": true
				""")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(consentsOf(email)).isEqualTo(new Consents(NOW, NOW));
		assertThat(profileOf(email)).isEqualTo("1999-03-01|FEMALE");
	}

	@Test
	void withoutValuesAndConsentsNothingIsKeptAndNoProfileIsMade() {
		String email = newEmail();

		assertThat(signup(email, "")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(consentsOf(email)).isEqualTo(new Consents(null, null));
		assertThat(profileOf(email)).isNull();
	}

	@Test
	void eachConsentIsKeptOnItsOwn() {
		// 값 없이 수집·이용에만 동의해도 남긴다. 나중에 프로필에 넣는 값을 통계에 쓸 수 있다
		String consentOnly = newEmail();
		assertThat(signup(consentOnly, "\"birthGenderConsent\": true, \"eventNoticeConsent\": false"))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(consentsOf(consentOnly)).isEqualTo(new Consents(NOW, null));
		assertThat(profileOf(consentOnly)).isNull();

		String noticeOnly = newEmail();
		assertThat(signup(noticeOnly, "\"eventNoticeConsent\": true")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(consentsOf(noticeOnly)).isEqualTo(new Consents(null, NOW));

		// 하나만 넣어도 된다. 성별은 대소문자를 가리지 않고, 빈 글자는 넣지 않은 것이다
		String genderOnly = newEmail();
		assertThat(signup(genderOnly, "\"birthDate\": null, \"gender\": \" MALE \", \"birthGenderConsent\": true"))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(profileOf(genderOnly)).isEqualTo("-|MALE");
		String blankGender = newEmail();
		assertThat(signup(blankGender, "\"gender\": \"\"")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(profileOf(blankGender)).isNull();
	}

	@Test
	void valuesWithoutTheConsentAre400AndNoAccountIsMade() {
		String email = newEmail();

		assertInvalid(signup(email, "\"birthDate\": \"1999-03-01\""), NEEDS_CONSENT);
		assertInvalid(signup(email, "\"gender\": \"male\", \"birthGenderConsent\": false, \"eventNoticeConsent\": true"),
				NEEDS_CONSENT);

		assertThat(this.jdbc.sql("select count(*) from users where email = :email").param("email", email)
			.query(Integer.class).single()).isZero();
	}

	@Test
	void theBirthDateIsFrom1900ToTodayInSeoulAndTheGenderIsMaleOrFemale() {
		assertInvalid(signup(newEmail(), values("2026-11-14", null)), BAD_BIRTH_DATE);
		assertInvalid(signup(newEmail(), values("1899-12-31", null)), BAD_BIRTH_DATE);
		// 서울의 오늘이다. UTC 의 날짜로 보면 내일이라 막힌다
		String today = newEmail();
		assertThat(signup(today, values("2026-11-13", null))).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(profileOf(today)).isEqualTo("2026-11-13|-");
		assertThat(signup(newEmail(), values("1900-01-01", null))).hasStatus(HttpStatus.NO_CONTENT);

		assertInvalid(signup(newEmail(), values(null, "other")), BAD_GENDER);
		// 날짜의 꼴이 틀리면 본문을 읽지 못한 것과 같은 공통 메시지다
		assertInvalid(signup(newEmail(), values("1999-13-01", null)), "입력값을 확인해 주세요");
		assertInvalid(signup(newEmail(), values("1999.03.01", null)), "입력값을 확인해 주세요");
	}

	@Test
	void signingUpAgainBeforeVerifyingReplacesTheEarlierValuesAndConsents() {
		// 남이 내 이메일로 생년월일·성별과 동의를 넣어 먼저 가입만 해 두었다
		String email = newEmail();
		signup(email, "\"birthDate\": \"1950-01-01\", \"gender\": \"male\", \"birthGenderConsent\": true, "
				+ "\"eventNoticeConsent\": true").exchange();

		assertThat(signup(email, "")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(consentsOf(email)).isEqualTo(new Consents(null, null));
		assertThat(profileOf(email)).isEqualTo("-|-");

		assertThat(signup(email, values("2000-01-02", "female"))).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(consentsOf(email)).isEqualTo(new Consents(NOW, null));
		assertThat(profileOf(email)).isEqualTo("2000-01-02|FEMALE");
	}

	@Test
	void aVerifiedEmailIs409AndItsAccountKeepsWhatItHas() {
		long user = this.fixtures.user("가람");
		String email = this.fixtures.emailOf(user);

		assertThat(signup(email, values("1999-03-01", "male") + ", \"eventNoticeConsent\": true"))
			.hasStatus(HttpStatus.CONFLICT);

		assertThat(consentsOf(email)).isEqualTo(new Consents(null, null));
		assertThat(profileOf(email)).isNull();
	}

	/** 동의한 때. 동의하지 않았으면 null. */
	private record Consents(Instant birthGender, Instant eventNotice) {
	}

	private Consents consentsOf(String email) {
		return this.jdbc.sql("select birth_gender_consent_at, event_notice_consent_at from users where email = :email")
			.param("email", email)
			.query((rs, rowNum) -> new Consents(instant(rs.getTimestamp(1)), instant(rs.getTimestamp(2))))
			.single();
	}

	/** 프로필의 "생년월일|성별"(빈 칸은 -). 프로필이 없으면 null. */
	private String profileOf(String email) {
		return this.jdbc.sql("""
				select coalesce(p.birth_date::text, '-') || '|' || coalesce(p.gender, '-')
				  from profiles p join users u on u.id = p.user_id
				 where u.email = :email
				""").param("email", email).query(String.class).optional().orElse(null);
	}

	/** 수집·이용에 동의하고 넣는 생년월일과 성별. null 이면 보내지 않는다. */
	private static String values(String birthDate, String gender) {
		return "\"birthGenderConsent\": true" + ((birthDate != null) ? ", \"birthDate\": \"" + birthDate + "\"" : "")
				+ ((gender != null) ? ", \"gender\": \"" + gender + "\"" : "");
	}

	/** extras 는 이름·이메일·비밀번호 뒤에 붙일 JSON 칸들이다. */
	private MockMvcTester.MockMvcRequestBuilder signup(String email, String extras) {
		String more = extras.isBlank() ? "" : ", " + extras.strip();
		return this.mvc.post().uri("/api/auth/signup").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{ \"name\": \"다온\", \"email\": \"%s\", \"password\": \"first password\"%s }".formatted(email, more));
	}

	private void assertInvalid(MockMvcTester.MockMvcRequestBuilder request, String message) {
		assertThat(request).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isStrictlyEqualTo("""
				{ "code": "INVALID_INPUT", "message": "%s" }
				""".formatted(message));
	}

	private static Instant instant(Timestamp timestamp) {
		return (timestamp != null) ? timestamp.toInstant() : null;
	}

	private static String newEmail() {
		return "values-" + UUID.randomUUID() + "@example.test";
	}

}
