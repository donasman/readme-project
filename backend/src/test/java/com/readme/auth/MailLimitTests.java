package com.readme.auth;

import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.readme.Fixtures;
import com.readme.IntegrationTest;
import com.readme.TestMailbox;

/**
 * 링크 메일을 보내는 제한(로그인 설계 4.1, 2026-11-15 정함). 같은 사람에게 같은 종류의 링크는 1분에 한 번,
 * 최근 24시간에 5번까지 보낸다. 제한에 걸려도 응답은 204 로 같고 메일만 가지 않는다.
 *
 * <p>시계가 멈춰 있으므로 시간이 지난 것은 보낸 링크의 created_at 을 앞으로 옮겨 만든다(Fixtures.ageLinks).
 */
@IntegrationTest
class MailLimitTests {

	private static final String PASSWORD = "first password";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TestMailbox mailbox;

	@Test
	void aSecondResetRequestWithinAMinuteSendsNothingAndTheFirstLinkStillWorks() {
		String email = this.fixtures.emailOf(this.fixtures.passwordUser("가람", PASSWORD, true));
		requestReset(email).exchange();
		String first = this.mailbox.lastTokenTo(email);

		assertThat(requestReset(email)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.mailbox.to(email)).hasSize(1);
		assertThat(linkRows(email)).isEqualTo(1);
		assertThat(post("/api/auth/password-reset/confirm", "{ \"token\": \"%s\", \"password\": \"new password\" }"
			.formatted(first))).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void aSecondVerificationMailWithinAMinuteIsNotSentAndTheFirstLinkStillWorks() {
		String email = "limit-" + UUID.randomUUID() + "@example.test";
		signup(email, PASSWORD).exchange();
		String first = this.mailbox.lastTokenTo(email);

		// 다시 보내기도, 다시 가입하기도 새 메일을 보내지 않는다
		assertThat(resend(email)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(signup(email, "second password")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.mailbox.to(email)).hasSize(1);
		assertThat(post("/api/auth/verify-email", "{ \"token\": \"%s\" }".formatted(first)))
			.hasStatus(HttpStatus.NO_CONTENT);
		// 다시 가입하며 넣은 비밀번호로는 바뀌었다
		assertThat(login(email, "second password")).hasStatusOk();
	}

	@Test
	void atMostFiveLinksOfAKindInTwentyFourHours() {
		// 인증 전 계정이라 재설정 메일과 가입 확인 메일을 모두 받는다
		String email = this.fixtures.emailOf(this.fixtures.passwordUser("가람", PASSWORD, false));
		for (int i = 0; i < AccountService.LINKS_PER_DAY; i++) {
			assertThat(requestReset(email)).hasStatus(HttpStatus.NO_CONTENT);
			this.fixtures.ageLinks(email, Duration.ofHours(2));
		}
		assertThat(this.mailbox.to(email)).hasSize(5);

		// 다섯 링크는 2~10시간 전의 것이다. 1분은 지났지만 24시간 안에 5번을 보냈다
		assertThat(requestReset(email)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.mailbox.to(email)).hasSize(5);

		// 가입 확인 메일은 따로 센다
		assertThat(resend(email)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.mailbox.to(email)).hasSize(6);

		// 가장 오래된 재설정 링크가 24시간을 지나면 다시 보낸다
		this.fixtures.ageLinks(email, Duration.ofHours(14).plusMinutes(1));
		assertThat(requestReset(email)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.mailbox.to(email)).hasSize(7);
		assertThat(this.mailbox.lastTo(email).getSubject()).isEqualTo("[리드미] 비밀번호 재설정");
	}

	private MockMvcTester.MockMvcRequestBuilder signup(String email, String password) {
		return post("/api/auth/signup", "{ \"name\": \"다온\", \"email\": \"%s\", \"password\": \"%s\" }"
			.formatted(email, password));
	}

	private MockMvcTester.MockMvcRequestBuilder resend(String email) {
		return post("/api/auth/verify-email/resend", "{ \"email\": \"%s\" }".formatted(email));
	}

	private MockMvcTester.MockMvcRequestBuilder requestReset(String email) {
		return post("/api/auth/password-reset/request", "{ \"email\": \"%s\" }".formatted(email));
	}

	private MockMvcTester.MockMvcRequestBuilder login(String email, String password) {
		return post("/api/auth/login", "{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, password));
	}

	private MockMvcTester.MockMvcRequestBuilder post(String uri, String body) {
		return this.mvc.post().uri(uri).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private int linkRows(String email) {
		return this.jdbc.sql("select count(*) from email_tokens where user_id = (select id from users where email = :email)")
			.param("email", email)
			.query(Integer.class)
			.single();
	}

}
