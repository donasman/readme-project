package com.readme.auth;

import static com.readme.TestRequests.setCookie;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.util.UriComponentsBuilder;

import com.readme.FixedClockConfiguration;
import com.readme.Fixtures;
import com.readme.IntegrationTest;
import com.readme.TestMailbox;

/**
 * 구글 로그인(로그인 설계 3.2, 4.1). 실제 보안 필터와 세션(spring_session)을 거치고, 구글은 부르지 않는다.
 *
 * <p>구글 클라이언트 값을 넣어 구글 로그인을 켜야 하므로 다른 통합 테스트와 서버·DB 를 나눠 쓰지 못하고 따로 띄운다.
 * 구글로 보내는 것(state, nonce 를 세션에 두기)과 돌아온 뒤의 처리는 실제 것이다. 구글 쪽(코드를 토큰으로 바꾸기, ID 토큰 검사,
 * 사용자 정보)만 {@link FakeGoogle} 이 대신 답한다.
 */
@IntegrationTest
@TestPropertySource(properties = {
		"spring.security.oauth2.client.registration.google.client-id=test-client-id",
		"spring.security.oauth2.client.registration.google.client-secret=test-client-secret" })
@Import(GoogleLoginTests.FakeGoogle.class)
class GoogleLoginTests {

	private static final String HOME = "http://localhost:5173/";

	private static final String LOGIN_ERROR = "http://localhost:5173/login?error=google";

	private static final String PASSWORD = "correct horse";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TestMailbox mailbox;

	@Autowired
	FakeGoogle google;

	@Test
	void startSendsTheBrowserToGoogleWithOurCallbackAddress() {
		MvcTestResult start = this.mvc.get().uri("/oauth2/authorization/google").exchange();

		assertThat(start).hasStatus(HttpStatus.FOUND);
		assertThat(start.getResponse().getRedirectedUrl()).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
		Map<String, String> query = query(start);
		assertThat(query).containsEntry("client_id", "test-client-id")
			.containsEntry("response_type", "code")
			.containsEntry("redirect_uri", "http://localhost/login/oauth2/code/google")
			.containsKeys("state", "nonce");
		assertThat(query.get("scope").split(" ")).containsExactlyInAnyOrder("openid", "email", "profile");
		// 돌아왔을 때 맞춰 볼 state 를 세션에 둔다
		assertThat(setCookie(start, "SESSION")).isNotNull();
	}

	@Test
	void aNewPersonGetsAVerifiedAccountWithoutAPasswordAndALoginSession() {
		String email = newEmail();

		MvcTestResult back = googleLogin(newSub(), email, true, "구글 가람");

		assertThat(back).hasStatus(HttpStatus.FOUND).hasRedirectedUrl(HOME);
		assertThat(me(back)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "name": "구글 가람", "email": "%s", "hasPassword": false, "providers": ["google"] }
				""".formatted(email));
		long user = userIdOf(email);
		assertThat(this.jdbc.sql("select email_verified_at is not null from users where id = :id").param("id", user)
			.query(Boolean.class).single()).isTrue();
		// 세션의 주인은 구글 계정이 아니라 사용자 id 다. 비밀번호를 재설정하면 이 이름으로 세션을 찾아 끊는다.
		assertThat(sessionsOf(String.valueOf(user))).isEqualTo(1);
		// 프런트가 로그인 직후 보낼 요청에 쓸 새 CSRF 토큰
		assertThat(setCookie(back, "XSRF-TOKEN")).isNotNull();
	}

	@Test
	void aLinkedGoogleAccountLogsInToItsAccountWhateverItsEmailSaysNow() {
		long user = this.fixtures.user("가람");
		String sub = newSub();
		link(user, sub);

		// 구글 쪽 이메일이 바뀌었고 확인 전이어도 sub 로 찾는다(3.2 의 1)
		MvcTestResult back = googleLogin(sub, newEmail(), false, "다른 이름");

		assertThat(back).hasRedirectedUrl(HOME);
		assertThat(me(back)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "name": "가람", "email": "%s" }
				""".formatted(this.fixtures.emailOf(user)));
	}

	@Test
	void aVerifiedGoogleEmailLinksToTheAccountWithThatEmail() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);
		String email = this.fixtures.emailOf(user);
		String sub = newSub();

		// 이메일은 대소문자를 가리지 않는다
		MvcTestResult back = googleLogin(sub, email.toUpperCase(Locale.ROOT), true, "구글 이름");

		assertThat(back).hasRedirectedUrl(HOME);
		// 이름과 비밀번호는 그대로다
		assertThat(me(back)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "name": "가람", "email": "%s", "hasPassword": true, "providers": ["google"] }
				""".formatted(email));
		// 다음부터는 sub 로 같은 계정에 들어온다
		assertThat(googleLogin(sub, email, true, "구글 이름")).hasRedirectedUrl(HOME);
		assertThat(identitiesOf(user)).isEqualTo(1);
	}

	@Test
	void anUnverifiedAccountWithThatEmailIsTakenOverAndItsPasswordAndLinkStopWorking() {
		// 누군가 이 이메일로 가입만 해 두었다(인증 전, 가입 확인 메일이 나갔다)
		String email = newEmail();
		assertThat(this.mvc.post().uri("/api/auth/signup").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{ \"name\": \"먼저 가입한 사람\", \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, PASSWORD)))
			.hasStatus(HttpStatus.NO_CONTENT);
		String verifyToken = this.mailbox.lastTokenTo(email);

		MvcTestResult back = googleLogin(newSub(), email, true, "이메일 주인");

		assertThat(back).hasRedirectedUrl(HOME);
		assertThat(me(back)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "name": "이메일 주인", "email": "%s", "hasPassword": false, "providers": ["google"] }
				""".formatted(email));
		// 먼저 가입한 사람의 비밀번호와 가입 확인 링크는 더 쓸 수 없다
		assertThat(this.mvc.post().uri("/api/auth/login").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, PASSWORD)))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.post().uri("/api/auth/verify-email").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{ \"token\": \"%s\" }".formatted(verifyToken)))
			.hasStatus(HttpStatus.GONE);
	}

	@Test
	void anEmailGoogleHasNotVerifiedIsNeitherLinkedNorUsedForANewAccount() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);
		String newEmail = newEmail();

		for (String email : List.of(this.fixtures.emailOf(user), newEmail)) {
			String sub = newSub();
			MvcTestResult back = googleLogin(sub, email, false, "누군가");

			assertThat(back).hasRedirectedUrl(LOGIN_ERROR);
			// 보안 필터가 세션에 넣어 둔 구글 로그인 정보는 남지 않는다
			assertThat(setCookie(back, "SESSION")).isNull();
			assertThat(sessionsOf(sub)).isZero();
		}
		assertThat(identitiesOf(user)).isZero();
		assertThat(this.jdbc.sql("select count(*) from users where lower(email) = lower(:email)").param("email", newEmail)
			.query(Integer.class).single()).isZero();
	}

	@Test
	void anAccountThatHasAnotherGoogleAccountIsNotLinkedAgain() {
		long user = this.fixtures.user("가람");
		link(user, newSub());

		assertThat(googleLogin(newSub(), this.fixtures.emailOf(user), true, "가람")).hasRedirectedUrl(LOGIN_ERROR);
		assertThat(identitiesOf(user)).isEqualTo(1);
	}

	@Test
	void aMissingNameUsesTheEmailAndALongNameIsCutToFit() {
		String noName = newEmail();
		String longName = newEmail();

		assertThat(me(googleLogin(newSub(), noName, true, null))).bodyJson().extractingPath("$.name")
			.isEqualTo(noName.substring(0, noName.indexOf('@')));
		assertThat(me(googleLogin(newSub(), longName, true, "가".repeat(60)))).bodyJson().extractingPath("$.name")
			.isEqualTo("가".repeat(50));
	}

	@Test
	void deniedConsentGoesBackToTheLoginScreen() {
		MvcTestResult start = this.mvc.get().uri("/oauth2/authorization/google").exchange();

		MvcTestResult back = this.mvc.get().uri("/login/oauth2/code/google")
			.param("error", "access_denied")
			.param("state", query(start).get("state"))
			.cookie(new Cookie("SESSION", setCookie(start, "SESSION")))
			.exchange();

		assertThat(back).hasRedirectedUrl(LOGIN_ERROR);
	}

	/** 구글로 보낸 뒤, 구글이 이 사람을 확인하고 돌려보낸 것처럼 돌아오는 주소를 부른다. */
	private MvcTestResult googleLogin(String sub, String email, boolean emailVerified, String name) {
		MvcTestResult start = this.mvc.get().uri("/oauth2/authorization/google").exchange();
		Map<String, String> query = query(start);
		Map<String, Object> idToken = new HashMap<>();
		idToken.put("sub", sub);
		idToken.put("email", email);
		idToken.put("email_verified", emailVerified);
		if (name != null) {
			idToken.put("name", name);
		}
		// 구글은 보낼 때의 nonce 를 ID 토큰에 넣어 돌려준다
		idToken.put("nonce", query.get("nonce"));
		this.google.nextIdToken = idToken;
		return this.mvc.get().uri("/login/oauth2/code/google")
			.param("code", "test-code")
			.param("state", query.get("state"))
			.cookie(new Cookie("SESSION", setCookie(start, "SESSION")))
			.exchange();
	}

	/** 돌아온 응답의 세션 쿠키로 GET /api/me. */
	private MvcTestResult me(MvcTestResult back) {
		return this.mvc.get().uri("/api/me").cookie(new Cookie("SESSION", setCookie(back, "SESSION"))).exchange();
	}

	private static Map<String, String> query(MvcTestResult start) {
		Map<String, String> query = new HashMap<>();
		UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl()).build().getQueryParams()
			.forEach((key, values) -> query.put(key, URLDecoder.decode(values.getFirst(), StandardCharsets.UTF_8)));
		return query;
	}

	private void link(long userId, String sub) {
		this.jdbc.sql("insert into user_identities (user_id, provider, provider_user_id) values (:userId, 'GOOGLE', :sub)")
			.param("userId", userId)
			.param("sub", sub)
			.update();
	}

	private long userIdOf(String email) {
		return this.jdbc.sql("select id from users where email = :email").param("email", email).query(Long.class).single();
	}

	private int identitiesOf(long userId) {
		return this.jdbc.sql("select count(*) from user_identities where user_id = :userId").param("userId", userId)
			.query(Integer.class).single();
	}

	private int sessionsOf(String principalName) {
		return this.jdbc.sql("select count(*) from spring_session where principal_name = :name").param("name", principalName)
			.query(Integer.class).single();
	}

	private static String newSub() {
		return "google-" + UUID.randomUUID();
	}

	private static String newEmail() {
		return UUID.randomUUID() + "@gmail.example";
	}

	/**
	 * 구글 대신 답한다: 코드를 토큰으로 바꾸기, ID 토큰 검사, 사용자 정보. 돌려줄 ID 토큰의 값은 테스트가 {@link #nextIdToken} 에 넣는다.
	 * 이 빈들이 있으면 oauth2Login 이 기본 것(구글을 부르는 것) 대신 쓴다.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	static class FakeGoogle {

		volatile Map<String, Object> nextIdToken = Map.of();

		@Bean
		OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> fakeTokenEndpoint() {
			return (request) -> OAuth2AccessTokenResponse.withToken("fake-access-token")
				.tokenType(OAuth2AccessToken.TokenType.BEARER)
				.expiresIn(3600)
				.additionalParameters(Map.of(OidcParameterNames.ID_TOKEN, "fake-id-token"))
				.build();
		}

		@Bean
		JwtDecoderFactory<ClientRegistration> fakeIdTokenDecoder() {
			return (client) -> (token) -> Jwt.withTokenValue(token)
				.header("alg", "RS256")
				.claims((claims) -> claims.putAll(this.nextIdToken))
				.issuedAt(FixedClockConfiguration.NOW)
				.expiresAt(FixedClockConfiguration.NOW.plusSeconds(3600))
				.build();
		}

		@Bean
		OAuth2UserService<OidcUserRequest, OidcUser> fakeUserInfo() {
			return (request) -> new DefaultOidcUser(List.of(), request.getIdToken());
		}

	}

}
