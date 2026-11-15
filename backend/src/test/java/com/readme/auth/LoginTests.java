package com.readme.auth;

import static com.readme.TestRequests.setCookie;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.readme.FixedClockConfiguration;
import com.readme.Fixtures;
import com.readme.IntegrationTest;

/**
 * 이메일·비밀번호 로그인과 로그아웃. 진짜 세션(spring_session 의 행)을 만들고, 그 세션으로 지금 로그인한 사람을 읽는다.
 *
 * <p>로그인 실패 제한(LoginThrottle)은 서버의 메모리에 있어 다른 테스트와 나눠 쓴다. 테스트마다 다른 이메일로 센다.
 * 시간이 지나 풀리는 것은 LoginThrottleTests 가 본다.
 */
@IntegrationTest
class LoginTests {

	private static final String PASSWORD = "correct horse";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	@Test
	void loginStoresASessionThatTheNextRequestReads() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);
		int before = sessionRows();

		MvcTestResult login = login(this.fixtures.emailOf(user), PASSWORD, null);

		assertThat(login).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{ "id": %d, "name": "가람", "email": "%s", "hasPassword": true, "providers": [], "google": null }
				""".formatted(user, this.fixtures.emailOf(user)));
		assertThat(sessionRows()).isEqualTo(before + 1);
		String session = setCookie(login, "SESSION");
		assertThat(session).isNotNull();
		assertThat(this.mvc.get().uri("/api/me").cookie(new Cookie("SESSION", session)))
			.hasStatusOk().bodyJson().extractingPath("$.name").isEqualTo("가람");
		assertThat(this.jdbc.sql("select last_login_at from users where id = :id").param("id", user)
			.query(Timestamp.class).single().toInstant()).isEqualTo(FixedClockConfiguration.NOW);
	}

	@Test
	void emailIsCaseInsensitiveAndTrimmed() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);

		assertThat(login("  " + this.fixtures.emailOf(user).toUpperCase() + " ", PASSWORD, null)).hasStatusOk();
	}

	@Test
	void wrongPasswordAndUnknownEmailAreTheSame401AndStoreNoSession() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);
		int before = sessionRows();

		for (MvcTestResult result : new MvcTestResult[] { login(this.fixtures.emailOf(user), "wrong password", null),
				login("nobody-" + user + "@example.test", PASSWORD, null) }) {
			assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).bodyJson().isStrictlyEqualTo("""
					{ "code": "INVALID_CREDENTIALS", "message": "이메일이나 비밀번호가 맞지 않습니다" }
					""");
			assertThat(setCookie(result, "SESSION")).isNull();
		}
		assertThat(sessionRows()).isEqualTo(before);
	}

	@Test
	void anAccountWithoutAPasswordCannotLogInWithOne() {
		// 구글로만 가입한 계정
		long user = this.fixtures.user("가람");

		assertThat(login(this.fixtures.emailOf(user), PASSWORD, null)).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson().extractingPath("$.code").isEqualTo("INVALID_CREDENTIALS");
	}

	@Test
	void aPasswordTooLongToHashIs401NotAnError() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);

		assertThat(login(this.fixtures.emailOf(user), "a".repeat(100), null)).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson().extractingPath("$.code").isEqualTo("INVALID_CREDENTIALS");
	}

	@Test
	void beforeVerifyingTheEmailTheRightPasswordIs403() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, false);

		assertThat(login(this.fixtures.emailOf(user), PASSWORD, null)).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson().extractingPath("$.code").isEqualTo("EMAIL_NOT_VERIFIED");
		// 틀린 비밀번호면 인증 전인지 알려 주지 않는다
		assertThat(login(this.fixtures.emailOf(user), "wrong password", null)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void loggingInAsAnotherUserSwitchesTheUserAndRenewsTheSessionId() {
		long first = this.fixtures.passwordUser("가람", PASSWORD, true);
		long second = this.fixtures.passwordUser("나래", PASSWORD, true);
		String firstSession = setCookie(login(this.fixtures.emailOf(first), PASSWORD, null), "SESSION");

		String secondSession = setCookie(login(this.fixtures.emailOf(second), PASSWORD, firstSession), "SESSION");

		assertThat(secondSession).isNotNull().isNotEqualTo(firstSession);
		assertThat(this.mvc.get().uri("/api/me").cookie(new Cookie("SESSION", secondSession)))
			.hasStatusOk().bodyJson().extractingPath("$.name").isEqualTo("나래");
		// 로그인 전의 세션 id 는 더 쓸 수 없다(세션 고정 공격 막기)
		assertThat(this.mvc.get().uri("/api/me").cookie(new Cookie("SESSION", firstSession)))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void loginGivesANewCsrfToken() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);

		assertThat(setCookie(login(this.fixtures.emailOf(user), PASSWORD, null), "XSRF-TOKEN"))
			.isNotNull().isNotEqualTo("test-xsrf-token");
	}

	@Test
	void aFirstReadGivesTheCsrfCookieThatLoginNeeds() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);

		// 프런트가 처음 여는 요청(로그인 전이라 401)에도 XSRF-TOKEN 쿠키가 실린다
		MvcTestResult first = this.mvc.get().uri("/api/me").exchange();
		assertThat(first).hasStatus(HttpStatus.UNAUTHORIZED);
		String token = setCookie(first, "XSRF-TOKEN");
		assertThat(token).isNotNull();

		// 그 쿠키를 헤더에 넣어 로그인한다(TestRequests.xsrf 없이 프런트처럼)
		assertThat(this.mvc.post().uri("/api/auth/login")
			.cookie(new Cookie("XSRF-TOKEN", token))
			.header("X-XSRF-TOKEN", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(this.fixtures.emailOf(user), PASSWORD)))
			.hasStatusOk();
	}

	@Test
	void logoutEndsTheSessionAndGivesANewCsrfToken() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);
		String session = setCookie(login(this.fixtures.emailOf(user), PASSWORD, null), "SESSION");
		int before = sessionRows();

		MvcTestResult logout = this.mvc.post().uri("/api/auth/logout").with(xsrf())
			.cookie(new Cookie("SESSION", session))
			.exchange();

		assertThat(logout).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(sessionRows()).isEqualTo(before - 1);
		assertThat(this.mvc.get().uri("/api/me").cookie(new Cookie("SESSION", session)))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		// 바로 다시 로그인할 수 있게 새 토큰을 준다(예전 토큰은 지운다)
		assertThat(setCookie(logout, "XSRF-TOKEN")).isNotNull().isNotEqualTo("test-xsrf-token");
	}

	@Test
	void logoutWithoutASessionIsStill204() {
		assertThat(this.mvc.post().uri("/api/auth/logout").with(xsrf())).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void tenFailuresBlockTheEmailEvenForTheRightPassword() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);
		String email = this.fixtures.emailOf(user);
		for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
			assertThat(login(email, "wrong password", null)).hasStatus(HttpStatus.UNAUTHORIZED);
		}

		MvcTestResult blocked = login(email, PASSWORD, null);

		assertThat(blocked).hasStatus(HttpStatus.TOO_MANY_REQUESTS).bodyJson().isStrictlyEqualTo("""
				{ "code": "TOO_MANY_ATTEMPTS", "message": "시도가 너무 많습니다. 잠시 뒤에 다시 시도해 주세요" }
				""");
		assertThat(setCookie(blocked, "SESSION")).isNull();
		// 대소문자와 앞뒤 공백이 달라도 같은 이메일이다
		assertThat(login(" " + email.toUpperCase() + " ", PASSWORD, null)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	void aDottedCapitalIDoesNotGetAroundTheBlock() {
		long user = this.fixtures.passwordUser("민지", PASSWORD, true);
		// Fixtures 의 이메일(UUID)에는 i 가 없다
		String email = "minji-" + UUID.randomUUID() + "@example.test";
		this.jdbc.sql("update users set email = :email where id = :id").param("email", email).param("id", user).update();
		String dotted = email.replaceFirst("i", "İ");
		// PostgreSQL 은 둘을 같은 이메일로 본다. 자바는 "İ"를 i 와 위 점으로 낮춰 다른 글자가 된다
		assertThat(this.jdbc.sql("select lower(:a) = lower(:b)").param("a", email).param("b", dotted)
			.query(Boolean.class).single()).isTrue();
		for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
			assertThat(login(email, "wrong password", null)).hasStatus(HttpStatus.UNAUTHORIZED);
		}

		// 새 칸으로 세어 막히지 않더라도, 그 글자로는 계정을 찾지 못한다
		assertThat(login(dotted, PASSWORD, null)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void anEmailWithoutAnAccountIsBlockedTheSameWay() {
		String nobody = "nobody-" + UUID.randomUUID() + "@example.test";
		for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
			assertThat(login(nobody, PASSWORD, null)).hasStatus(HttpStatus.UNAUTHORIZED);
		}

		assertThat(login(nobody, PASSWORD, null)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	void aSuccessfulLoginClearsTheFailures() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);
		String email = this.fixtures.emailOf(user);
		for (int i = 0; i < LoginThrottle.MAX_FAILURES - 1; i++) {
			assertThat(login(email, "wrong password", null)).hasStatus(HttpStatus.UNAUTHORIZED);
		}
		assertThat(login(email, PASSWORD, null)).hasStatusOk();

		// 지우지 않았다면 여기서 막힌다
		for (int i = 0; i < LoginThrottle.MAX_FAILURES - 1; i++) {
			assertThat(login(email, "wrong password", null)).hasStatus(HttpStatus.UNAUTHORIZED);
		}
		assertThat(login(email, PASSWORD, null)).hasStatusOk();
	}

	@Test
	void loginChecksTheInput() {
		// 이메일은 users 칸의 길이(320자)까지 받는다
		String tooLong = "a".repeat(308) + "@example.test";
		for (String body : new String[] { "{}", "{ \"email\": \"a@example.test\" }", "{ \"password\": \"x\" }",
				body(tooLong, PASSWORD) }) {
			assertThat(this.mvc.post().uri("/api/auth/login").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
		}
	}

	private MvcTestResult login(String email, String password, String session) {
		MockMvcTester.MockMvcRequestBuilder request = this.mvc.post().uri("/api/auth/login")
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(email, password));
		if (session != null) {
			request = request.cookie(new Cookie("SESSION", session));
		}
		return request.exchange();
	}

	private static String body(String email, String password) {
		return "{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, password);
	}

	private int sessionRows() {
		return this.jdbc.sql("select count(*) from spring_session").query(Integer.class).single();
	}

}
