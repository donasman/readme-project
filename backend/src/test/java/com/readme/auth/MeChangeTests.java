package com.readme.auth;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.setCookie;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

/**
 * 내 계정 고치기: 표시 이름(PUT /api/me), 로그인한 채로 비밀번호 바꾸기(PUT /api/me/password). 로그인 설계 4절, 2026-11-15.
 */
@IntegrationTest
class MeChangeTests {

	private static final String OLD = "old password";

	private static final String NEW = "new password";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	// ── 표시 이름 ──────────────────────────────────────────────────────

	@Test
	void renameStripsTheNameAndReturnsMyAccount() {
		long me = this.fixtures.user("다온");

		assertThat(rename(me, "  새 이름  ")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "id": %d, "name": "새 이름" }
				""".formatted(me));
		assertThat(this.mvc.get().uri("/api/me").with(loginAs(me))).bodyJson()
			.extractingPath("$.name").isEqualTo("새 이름");
	}

	@Test
	void theNameIsRequiredAndFitsTheColumn() {
		long me = this.fixtures.user("다온");

		for (String name : new String[] { "   ", "가".repeat(51) }) {
			assertThat(rename(me, name)).hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
		}
		assertThat(this.mvc.put().uri("/api/me").with(loginAs(me)).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(rename(me, "가".repeat(50))).hasStatusOk();
		assertThat(this.mvc.put().uri("/api/me").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{ \"name\": \"누구\" }")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// ── 비밀번호 ───────────────────────────────────────────────────────

	@Test
	void theRightCurrentPasswordChangesItAndEndsMyOtherLogins() {
		long me = this.fixtures.passwordUser("가람", OLD, true);
		String email = this.fixtures.emailOf(me);
		String phone = setCookie(login(email, OLD), "SESSION");
		String laptop = setCookie(login(email, OLD), "SESSION");

		assertThat(changePassword(phone, OLD, NEW)).hasStatus(HttpStatus.NO_CONTENT);

		// 바꾼 브라우저는 그대로 로그인해 있고, 다른 기기는 끊긴다
		assertThat(this.mvc.get().uri("/api/me").cookie(new Cookie("SESSION", phone))).hasStatusOk();
		assertThat(this.mvc.get().uri("/api/me").cookie(new Cookie("SESSION", laptop)))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(login(email, OLD)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(login(email, NEW)).hasStatusOk();
	}

	@Test
	void aWrongCurrentPasswordIs400AndKeepsThePassword() {
		long me = this.fixtures.passwordUser("가람", OLD, true);
		String email = this.fixtures.emailOf(me);
		String phone = setCookie(login(email, OLD), "SESSION");

		// 401 이 아니다. 프런트는 401 을 "로그인하지 않았다"로 본다
		assertThat(changePassword(phone, "wrong password", NEW)).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().isStrictlyEqualTo("""
					{ "code": "WRONG_PASSWORD", "message": "비밀번호가 맞지 않습니다" }
					""");
		assertThat(changePassword(phone, null, NEW)).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().extractingPath("$.code").isEqualTo("WRONG_PASSWORD");
		assertThat(this.mvc.get().uri("/api/me").cookie(new Cookie("SESSION", phone))).hasStatusOk();
		assertThat(login(email, OLD)).hasStatusOk();
	}

	@Test
	void theNewPasswordFollowsTheRule() {
		long me = this.fixtures.passwordUser("가람", OLD, true);
		String phone = setCookie(login(this.fixtures.emailOf(me), OLD), "SESSION");

		assertThat(changePassword(phone, OLD, "short")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
	}

	@Test
	void anAccountWithoutAPasswordIs409() {
		// 구글로만 가입한 계정
		long me = this.fixtures.user("가람");

		assertThat(this.mvc.put().uri("/api/me/password").with(loginAs(me)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"currentPassword\": \"\", \"newPassword\": \"%s\" }".formatted(NEW)))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson().extractingPath("$.code").isEqualTo("NO_PASSWORD");
	}

	private MvcTestResult rename(long userId, String name) {
		return this.mvc.put().uri("/api/me").with(loginAs(userId)).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{ \"name\": \"%s\" }".formatted(name)).exchange();
	}

	private MvcTestResult changePassword(String session, String current, String next) {
		String body = (current != null)
				? "{ \"currentPassword\": \"%s\", \"newPassword\": \"%s\" }".formatted(current, next)
				: "{ \"newPassword\": \"%s\" }".formatted(next);
		return this.mvc.put().uri("/api/me/password").cookie(new Cookie("SESSION", session)).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private MvcTestResult login(String email, String password) {
		return this.mvc.post().uri("/api/auth/login").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, password)).exchange();
	}

}
