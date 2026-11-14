package com.readme.auth;

import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.readme.Fixtures;
import com.readme.IntegrationTest;
import com.readme.TestMailbox;

/**
 * 가입, 가입 확인 메일, 이메일 인증(로그인 설계 4절, 2026-11-14 정한 것).
 */
@IntegrationTest
class SignupTests {

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
	void signupSendsAVerificationLinkAndTheAccountWaitsForIt() {
		String email = newEmail();

		assertThat(signup(" 다온 ", " " + email + " ", PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

		SimpleMailMessage mail = this.mailbox.lastTo(email);
		assertThat(mail.getFrom()).isEqualTo("리드미 <no-reply@readme.test>");
		assertThat(mail.getSubject()).isEqualTo("[리드미] 이메일을 확인해 주세요");
		assertThat(mail.getText()).contains("http://localhost:5173/verify-email?token=" + this.mailbox.lastTokenTo(email))
			.contains("24시간");
		// 이름과 이메일은 앞뒤 공백을 지우고, 비밀번호는 해시로만 둔다
		assertThat(this.jdbc.sql("""
				select display_name || '|' || email || '|' || (email_verified_at is null) || '|' || (password_hash like '{bcrypt}%')
				  from users where email = :email
				""").param("email", email).query(String.class).single()).isEqualTo("다온|" + email + "|true|true");
		// 링크의 토큰도 해시로만 둔다
		assertThat(this.jdbc.sql("select token_hash from email_tokens t join users u on u.id = t.user_id where u.email = :email")
			.param("email", email).query(String.class).single()).hasSize(64).isNotEqualTo(this.mailbox.lastTokenTo(email));
		assertThat(login(email, PASSWORD)).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson().extractingPath("$.code").isEqualTo("EMAIL_NOT_VERIFIED");
	}

	@Test
	void theLinkVerifiesTheEmailOnceAndThenLoginWorks() {
		String email = newEmail();
		signup("다온", email, PASSWORD).exchange();
		String token = this.mailbox.lastTokenTo(email);

		assertThat(verify(token)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(login(email, PASSWORD)).hasStatusOk().bodyJson().extractingPath("$.name").isEqualTo("다온");
		assertThat(verify(token)).hasStatus(HttpStatus.GONE).bodyJson().isStrictlyEqualTo("""
				{ "code": "LINK_EXPIRED", "message": "쓸 수 없는 링크입니다. 메일을 다시 받아 주세요" }
				""");
	}

	@Test
	void expiredAndUnknownLinksAre410() {
		String email = newEmail();
		signup("다온", email, PASSWORD).exchange();
		String token = this.mailbox.lastTokenTo(email);
		// 24시간이 지났다
		this.jdbc.sql("""
				update email_tokens set expires_at = created_at
				 where user_id = (select id from users where email = :email)
				""").param("email", email).update();

		assertThat(verify(token)).hasStatus(HttpStatus.GONE).bodyJson().extractingPath("$.code").isEqualTo("LINK_EXPIRED");
		assertThat(verify("no-such-token")).hasStatus(HttpStatus.GONE);
	}

	@Test
	void aVerifiedEmailIsTakenWhateverItsCase() {
		long user = this.fixtures.user("가람");

		assertThat(signup("다온", this.fixtures.emailOf(user).toUpperCase(), PASSWORD)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson().isStrictlyEqualTo("""
					{ "code": "EMAIL_TAKEN", "message": "이미 가입된 이메일입니다" }
					""");
		assertThat(this.mailbox.to(this.fixtures.emailOf(user))).isEmpty();
	}

	@Test
	void signingUpAgainBeforeVerifyingReplacesTheNameAndPasswordAndTheOldLink() {
		// 남이 내 이메일로 먼저 가입만 해 두었다
		String email = newEmail();
		signup("남", email, "someone else's").exchange();
		String oldToken = this.mailbox.lastTokenTo(email);

		assertThat(signup("다온", email, PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

		String newToken = this.mailbox.lastTokenTo(email);
		assertThat(newToken).isNotEqualTo(oldToken);
		assertThat(verify(oldToken)).hasStatus(HttpStatus.GONE);
		assertThat(verify(newToken)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(login(email, "someone else's")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(login(email, PASSWORD)).hasStatusOk().bodyJson().extractingPath("$.name").isEqualTo("다온");
		assertThat(this.jdbc.sql("select count(*) from users where lower(email) = lower(:email)").param("email", email)
			.query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void resendSendsANewLinkOnlyToAnUnverifiedAccount() {
		String email = newEmail();
		signup("다온", email, PASSWORD).exchange();
		String oldToken = this.mailbox.lastTokenTo(email);

		assertThat(resend(email)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.mailbox.to(email)).hasSize(2);
		assertThat(verify(oldToken)).hasStatus(HttpStatus.GONE);
		assertThat(verify(this.mailbox.lastTokenTo(email))).hasStatus(HttpStatus.NO_CONTENT);
		// 인증을 마쳤거나 없는 계정도 같은 응답이고 메일은 가지 않는다
		assertThat(resend(email)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.mailbox.to(email)).hasSize(2);
		String nobody = newEmail();
		assertThat(resend(nobody)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.mailbox.to(nobody)).isEmpty();
	}

	@Test
	void thePasswordIsAtLeast8CharactersAndAt72Bytes() {
		assertInvalid(signup("다온", newEmail(), "7 chars"));
		assertThat(signup("다온", newEmail(), "8 chars!")).hasStatus(HttpStatus.NO_CONTENT);
		// 한글은 한 글자가 3바이트다. 24자는 72바이트, 25자는 75바이트
		assertThat(signup("다온", newEmail(), "가".repeat(24))).hasStatus(HttpStatus.NO_CONTENT);
		assertInvalid(signup("다온", newEmail(), "가".repeat(25)));
		assertInvalid(signup("다온", newEmail(), "a".repeat(73)));
	}

	@Test
	void signupChecksTheNameAndEmail() {
		assertInvalid(signup(" ", newEmail(), PASSWORD));
		assertInvalid(signup("가".repeat(51), newEmail(), PASSWORD));
		assertInvalid(signup("다온", "not an email", PASSWORD));
		assertInvalid(this.mvc.post().uri("/api/auth/signup").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{ \"name\": \"다온\", \"email\": \"%s\" }".formatted(newEmail())));
	}

	private MockMvcTester.MockMvcRequestBuilder signup(String name, String email, String password) {
		return post("/api/auth/signup", "{ \"name\": \"%s\", \"email\": \"%s\", \"password\": \"%s\" }"
			.formatted(name, email, password));
	}

	private MockMvcTester.MockMvcRequestBuilder verify(String token) {
		return post("/api/auth/verify-email", "{ \"token\": \"%s\" }".formatted(token));
	}

	private MockMvcTester.MockMvcRequestBuilder resend(String email) {
		return post("/api/auth/verify-email/resend", "{ \"email\": \"%s\" }".formatted(email));
	}

	private MockMvcTester.MockMvcRequestBuilder login(String email, String password) {
		return post("/api/auth/login", "{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, password));
	}

	private MockMvcTester.MockMvcRequestBuilder post(String uri, String body) {
		return this.mvc.post().uri(uri).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private void assertInvalid(MockMvcTester.MockMvcRequestBuilder request) {
		assertThat(request).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
	}

	private static String newEmail() {
		return "signup-" + UUID.randomUUID() + "@example.test";
	}

}
