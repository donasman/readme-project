package com.readme.auth;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
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
import com.readme.TestMailbox;
import com.readme.common.token.Tokens;

/**
 * 링크 메일을 보내는 제한(로그인 설계 4.1, 2026-11-15 정함). 같은 사람에게 같은 종류의 링크는 1분에 한 번,
 * 최근 24시간에 5번까지 보낸다. 제한에 걸려도 응답은 204 로 같고 메일만 가지 않는다.
 *
 * <p>시계가 멈춰 있으므로 시간이 지난 것은 보낸 링크의 created_at 을 앞으로 옮겨 만든다(Fixtures.ageLinks).
 *
 * <p>제한이 세는 24시간이 지났고 더 쓸 수 없는 링크 행은 매일 지운다(AccountService.deleteSpentLinks, 2026-11-16 정함).
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

	@Autowired
	AccountService accounts;

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

	@Test
	void theDailyCleanupDeletesOnlyUsedOrExpiredLinksTheLimitNoLongerCounts() {
		long userId = this.fixtures.passwordUser("가람", PASSWORD, true);
		// 이 시각과 같거나 앞서 만든 링크는 제한이 세지 않는다(created_at > NOW - 24시간만 센다)
		Instant uncounted = NOW.minus(AccountService.LINK_COUNT_WINDOW);
		// 지운다: 세지 않는 때에 만들어 지났거나, 그보다 앞서 만들어 썼다
		link(userId, uncounted, uncounted.plus(Duration.ofHours(1)), null);
		link(userId, uncounted.minus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(1)), uncounted);
		// 남긴다: 1초 늦게 만들어 아직 센다, 오래되었지만 아직 쓸 수 있다
		long stillCounted = link(userId, uncounted.plusSeconds(1), uncounted.plus(Duration.ofHours(1)), NOW);
		long stillUsable = link(userId, uncounted.minus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(1)), null);

		this.accounts.deleteSpentLinks();

		assertThat(this.jdbc.sql("select id from email_tokens where user_id = :userId")
			.param("userId", userId)
			.query(Long.class)
			.list()).containsExactlyInAnyOrder(stillCounted, stillUsable);
	}

	private long link(long userId, Instant createdAt, Instant expiresAt, Instant usedAt) {
		return this.jdbc.sql("""
				insert into email_tokens (user_id, purpose, token_hash, expires_at, used_at, created_at)
				values (:userId, 'RESET_PASSWORD', :hash, :expiresAt, :usedAt, :createdAt) returning id
				""")
			.param("userId", userId)
			.param("hash", Tokens.sha256Hex(Tokens.newToken()))
			.param("expiresAt", Timestamp.from(expiresAt))
			.param("usedAt", (usedAt != null) ? Timestamp.from(usedAt) : null, Types.TIMESTAMP)
			.param("createdAt", Timestamp.from(createdAt))
			.query(Long.class)
			.single();
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
