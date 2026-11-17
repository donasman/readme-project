package com.readme.auth;

import static com.readme.TestRequests.setCookie;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.readme.FixedClockConfiguration;
import com.readme.Fixtures;
import com.readme.IntegrationTest;
import com.readme.TestMailbox;
import com.readme.common.token.Tokens;

/**
 * 비밀번호 재설정: 메일 받기, 링크 미리 확인, 새 비밀번호(로그인 설계 4절, 2026-11-14 정한 것).
 */
@IntegrationTest
class PasswordResetTests {

	private static final String OLD = "old password";

	private static final String NEW = "new password";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TestMailbox mailbox;

	@Test
	void requestSendsALinkOnlyWhenTheAccountExists() {
		String email = this.fixtures.emailOf(this.fixtures.passwordUser("가람", OLD, true));

		assertThat(request(email)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.mailbox.lastTo(email).getSubject()).isEqualTo("[리드미] 비밀번호 재설정");
		assertThat(this.mailbox.lastTo(email).getText())
			.contains("http://localhost:5173/password-reset/new?token=" + this.mailbox.lastTokenTo(email))
			.contains("1시간");
		// 없는 계정도 같은 응답이고 메일은 가지 않는다
		String nobody = "nobody-" + UUID.randomUUID() + "@example.test";
		assertThat(request(nobody)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.mailbox.to(nobody)).isEmpty();
	}

	@Test
	void checkTellsWhetherTheLinkIsStillUsableWithoutUsingIt() {
		String email = this.fixtures.emailOf(this.fixtures.passwordUser("가람", OLD, true));
		request(email).exchange();
		String token = this.mailbox.lastTokenTo(email);

		assertThat(check(token)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(check(token)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(confirm(token, NEW)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(check(token)).hasStatus(HttpStatus.GONE).bodyJson().extractingPath("$.code").isEqualTo("LINK_EXPIRED");
		assertThat(check("no-such-token")).hasStatus(HttpStatus.GONE);
	}

	@Test
	void confirmChangesThePasswordAndEndsEveryLogin() {
		long user = this.fixtures.passwordUser("가람", OLD, true);
		String email = this.fixtures.emailOf(user);
		String phone = setCookie(login(email, OLD).exchange(), "SESSION");
		String laptop = setCookie(login(email, OLD).exchange(), "SESSION");
		request(email).exchange();

		assertThat(confirm(this.mailbox.lastTokenTo(email), NEW)).hasStatus(HttpStatus.NO_CONTENT);

		for (String session : new String[] { phone, laptop }) {
			assertThat(this.mvc.get().uri("/api/me").cookie(new Cookie("SESSION", session)))
				.hasStatus(HttpStatus.UNAUTHORIZED);
		}
		assertThat(login(email, OLD)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(login(email, NEW)).hasStatusOk();
	}

	@Test
	void resettingLiftsTheLoginBlock() {
		// 비밀번호를 잊어 10번 틀린 사람이 재설정한다
		String email = this.fixtures.emailOf(this.fixtures.passwordUser("가람", OLD, true));
		for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
			assertThat(login(email, "wrong password")).hasStatus(HttpStatus.UNAUTHORIZED);
		}
		assertThat(login(email, OLD)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		request(email).exchange();

		assertThat(confirm(this.mailbox.lastTokenTo(email), NEW)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(login(email, NEW)).hasStatusOk();
	}

	@Test
	void theLinkWorksOnceAndANewRequestEndsTheOlderLink() {
		String email = this.fixtures.emailOf(this.fixtures.passwordUser("가람", OLD, true));
		request(email).exchange();
		String older = this.mailbox.lastTokenTo(email);
		this.fixtures.ageLinks(email, Duration.ofMinutes(1));
		request(email).exchange();
		String newer = this.mailbox.lastTokenTo(email);

		assertThat(confirm(older, NEW)).hasStatus(HttpStatus.GONE);
		assertThat(confirm(newer, NEW)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(confirm(newer, "another password")).hasStatus(HttpStatus.GONE);
		assertThat(login(email, NEW)).hasStatusOk();
	}

	@Test
	void confirmEndsTheOtherResetLinksButKeepsTheirRows() {
		String email = this.fixtures.emailOf(this.fixtures.passwordUser("가람", OLD, true));
		request(email).exchange();
		// 같이 들어온 요청 둘이 모두 링크를 보냈다
		String other = Tokens.newToken();
		this.jdbc.sql("""
				insert into email_tokens (user_id, purpose, token_hash, expires_at, created_at)
				values ((select id from users where email = :email), 'RESET_PASSWORD', :hash, :expiresAt, :now)
				""")
			.param("hash", Tokens.sha256Hex(other))
			.param("expiresAt", Timestamp.from(FixedClockConfiguration.NOW.plus(Duration.ofHours(1))))
			.param("now", Timestamp.from(FixedClockConfiguration.NOW))
			.param("email", email)
			.update();

		assertThat(confirm(this.mailbox.lastTokenTo(email), NEW)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(check(other)).hasStatus(HttpStatus.GONE);
		// 보낸 수를 세려고 행은 남긴다
		assertThat(this.jdbc.sql("""
				select count(*) from email_tokens
				 where user_id = (select id from users where email = :email) and used_at is not null
				""").param("email", email).query(Integer.class).single()).isEqualTo(2);
	}

	@Test
	void anExpiredLinkIs410() {
		String email = this.fixtures.emailOf(this.fixtures.passwordUser("가람", OLD, true));
		request(email).exchange();
		this.jdbc.sql("""
				update email_tokens set expires_at = created_at
				 where user_id = (select id from users where email = :email)
				""").param("email", email).update();

		assertThat(confirm(this.mailbox.lastTokenTo(email), NEW)).hasStatus(HttpStatus.GONE);
		assertThat(login(email, OLD)).hasStatusOk();
	}

	@Test
	void resettingAnUnverifiedAccountAlsoVerifiesIt() {
		// 재설정 메일을 받았다는 것이 그 이메일의 주인이라는 뜻이다
		String email = this.fixtures.emailOf(this.fixtures.passwordUser("가람", OLD, false));
		request(email).exchange();

		assertThat(confirm(this.mailbox.lastTokenTo(email), NEW)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(login(email, NEW)).hasStatusOk();
	}

	@Test
	void anAccountWithoutAPasswordGetsOneThroughReset() {
		// 구글로만 가입한 계정
		long user = this.fixtures.user("가람");
		String email = this.fixtures.emailOf(user);
		request(email).exchange();

		assertThat(confirm(this.mailbox.lastTokenTo(email), NEW)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(login(email, NEW)).hasStatusOk().bodyJson().extractingPath("$.hasPassword").isEqualTo(true);
	}

	@Test
	void aPasswordAgainstTheRuleIs400AndKeepsTheLink() {
		String email = this.fixtures.emailOf(this.fixtures.passwordUser("가람", OLD, true));
		request(email).exchange();
		String token = this.mailbox.lastTokenTo(email);

		assertThat(confirm(token, "short")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");

		assertThat(check(token)).hasStatus(HttpStatus.NO_CONTENT);
	}

	private MockMvcTester.MockMvcRequestBuilder request(String email) {
		return post("/api/auth/password-reset/request", "{ \"email\": \"%s\" }".formatted(email));
	}

	private MockMvcTester.MockMvcRequestBuilder check(String token) {
		return post("/api/auth/password-reset/check", "{ \"token\": \"%s\" }".formatted(token));
	}

	private MockMvcTester.MockMvcRequestBuilder confirm(String token, String password) {
		return post("/api/auth/password-reset/confirm", "{ \"token\": \"%s\", \"password\": \"%s\" }".formatted(token, password));
	}

	private MockMvcTester.MockMvcRequestBuilder login(String email, String password) {
		return post("/api/auth/login", "{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, password));
	}

	private MockMvcTester.MockMvcRequestBuilder post(String uri, String body) {
		return this.mvc.post().uri(uri).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
