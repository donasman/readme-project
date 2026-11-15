package com.readme.auth;

import static com.readme.FixedClockConfiguration.NOW;
import static com.readme.TestRequests.setCookie;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.util.UriComponentsBuilder;

import com.readme.FixedClockConfiguration;
import com.readme.Fixtures;
import com.readme.IntegrationTest;
import com.readme.TestMailbox;

/**
 * 구글 로그인과 로그인한 채로 구글 연결하기(로그인 설계 3.2, 4.1). 실제 보안 필터와 세션(spring_session)을 거치고, 구글은 부르지 않는다.
 *
 * <p>구글 클라이언트 값을 넣어 구글 로그인을 켜야 하므로 다른 통합 테스트와 서버·DB 를 나눠 쓰지 못하고 따로 띄운다.
 * 구글로 보내는 것(state, nonce 를 세션에 두기)과 돌아온 뒤의 처리는 실제 것이다. 구글 쪽(코드를 토큰으로 바꾸기, ID 토큰 검사,
 * 사용자 정보)만 {@link FakeGoogle} 이 대신 답한다. 생년월일·성별을 읽는 People API 는 내 PC 에 띄운 가짜({@link FakePeople})를
 * 실제 HTTP 로 부른다. 요청 범위는 운영(application-prod.yml)과 같다.
 */
@IntegrationTest
@TestPropertySource(properties = {
		"spring.security.oauth2.client.registration.google.client-id=test-client-id",
		"spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
		"spring.security.oauth2.client.registration.google.scope=openid,email,profile,"
				+ GooglePeople.BIRTHDAY_SCOPE + "," + GooglePeople.GENDER_SCOPE })
@Import(GoogleLoginTests.FakeGoogle.class)
class GoogleLoginTests {

	private static final FakePeople PEOPLE = new FakePeople();

	/** 프로필의 생일(대표)은 연도를 숨겼고, 구글 계정의 생일에 연도가 있다. 성별은 여성. */
	private static final String PERSON = """
			{ "resourceName": "people/1",
			  "birthdays": [
			    { "metadata": { "primary": true, "source": { "type": "PROFILE" } }, "date": { "month": 3, "day": 1 } },
			    { "metadata": { "source": { "type": "ACCOUNT" } }, "date": { "year": 1999, "month": 3, "day": 1 } } ],
			  "genders": [ { "metadata": { "primary": true }, "value": "female", "formattedValue": "Female" } ] }
			""";

	private static final String HOME = "http://localhost:5173/";

	private static final String LOGIN_ERROR = "http://localhost:5173/login?error=google";

	/** 구글 연결을 마치면 돌아가는 내 정보. 뒤에 결과가 붙는다. */
	private static final String ACCOUNT = "http://localhost:5173/settings/account?google=";

	private static final String PASSWORD = "correct horse";

	/** 브라우저가 구글에 가기 전부터 들고 있던 CSRF 토큰. */
	private static final String OLD_XSRF_TOKEN = "old";

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

	@DynamicPropertySource
	static void peopleApi(DynamicPropertyRegistry registry) {
		registry.add("app.google.people-url", PEOPLE::url);
	}

	@AfterAll
	static void stopPeopleApi() {
		PEOPLE.server.stop(0);
	}

	@BeforeEach
	void grantEverythingAndAnswerNothing() {
		this.google.grantedScopes = null;
		PEOPLE.answer(200, "{}");
	}

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
		assertThat(query.get("scope").split(" ")).containsExactlyInAnyOrder("openid", "email", "profile",
				"https://www.googleapis.com/auth/user.birthday.read", "https://www.googleapis.com/auth/user.gender.read");
		// 돌아왔을 때 맞춰 볼 state 를 세션에 둔다
		assertThat(setCookie(start, "SESSION")).isNotNull();
	}

	@Test
	void aNewPersonGetsAVerifiedAccountWithoutAPasswordAndALoginSession() {
		String email = newEmail();
		MvcTestResult start = this.mvc.get().uri("/oauth2/authorization/google").exchange();

		MvcTestResult back = googleLogin(start, newSub(), email, true, "구글 가람");

		assertThat(back).hasStatus(HttpStatus.FOUND).hasRedirectedUrl(HOME);
		// 구글로 가기 전에 만든 세션의 id 는 쓰지 않는다(세션 고정 공격 막기)
		assertThat(setCookie(back, "SESSION")).isNotNull().isNotEqualTo(setCookie(start, "SESSION"));
		assertThat(me(back)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "name": "구글 가람", "email": "%s", "hasPassword": false, "providers": ["google"] }
				""".formatted(email));
		long user = userIdOf(email);
		assertThat(this.jdbc.sql("select email_verified_at is not null from users where id = :id").param("id", user)
			.query(Boolean.class).single()).isTrue();
		// 세션의 주인은 구글 계정이 아니라 사용자 id 다. 비밀번호를 재설정하면 이 이름으로 세션을 찾아 끊는다.
		assertThat(sessionsOf(String.valueOf(user))).isEqualTo(1);
		// 프런트가 로그인 직후 보낼 요청에 쓸 새 CSRF 토큰. 토큰을 지우는 빈 쿠키가 앞에 실리므로 마지막 쿠키를 본다.
		// setCookie 는 빈 값을 건너뛰어 마지막이 지우는 쿠키여도 잡지 못하므로 헤더를 직접 읽는다.
		List<String> xsrfCookies = back.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
			.filter((header) -> header.startsWith("XSRF-TOKEN="))
			.toList();
		assertThat(xsrfCookies).isNotEmpty();
		String last = xsrfCookies.getLast();
		assertThat(last.substring("XSRF-TOKEN=".length(), last.indexOf(';'))).isNotEmpty().isNotEqualTo(OLD_XSRF_TOKEN);
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

	// ── 로그인한 채로 구글 연결하기(4.1, 2026-11-16) ─────────────────────────

	@Test
	void linkingWhileLoggedInAddsTheGoogleAccountWhateverItsEmail() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);
		String googleEmail = newEmail();
		String sub = newSub();

		// 구글 계정의 이메일이 계정의 이메일과 다르고, 구글이 확인하지 않은 이메일이어도 연결한다
		MvcTestResult back = linkGoogle(login(user, null), sub, googleEmail, false);

		assertThat(back).hasStatus(HttpStatus.FOUND).hasRedirectedUrl(ACCOUNT + "linked");
		// 연결을 시작한 사람으로 로그인한 채다
		assertThat(me(back)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "id": %d, "name": "가람", "email": "%s", "hasPassword": true, "providers": ["google"],
				  "google": { "email": "%s" } }
				""".formatted(user, this.fixtures.emailOf(user), googleEmail));
		// 다음부터는 그 구글 계정으로 이 계정에 로그인한다
		assertThat(me(googleLogin(sub, googleEmail, false, "구글 이름"))).bodyJson().isLenientlyEqualTo("""
				{ "id": %d }
				""".formatted(user));
	}

	@Test
	void aGoogleAccountLinkedToAnotherAccountStaysThere() {
		long other = this.fixtures.user("나래");
		String sub = newSub();
		link(other, sub);
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);

		MvcTestResult back = linkGoogle(login(user, null), sub, newEmail(), true);

		assertThat(back).hasRedirectedUrl(ACCOUNT + "in-use");
		// 그 구글 계정의 사람(나래)으로 바뀌지 않는다
		assertThat(me(back)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "id": %d, "providers": [], "google": null }
				""".formatted(user));
		assertThat(googleUserOf(sub)).isEqualTo(other);
	}

	@Test
	void anAccountThatAlreadyHasGoogleKeepsIt() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);
		String first = newSub();
		link(user, first);

		MvcTestResult back = linkGoogle(login(user, null), newSub(), newEmail(), true);

		assertThat(back).hasRedirectedUrl(ACCOUNT + "has-google");
		assertThat(me(back)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "id": %d }
				""".formatted(user));
		assertThat(identitiesOf(user)).isEqualTo(1);
		assertThat(googleUserOf(first)).isEqualTo(user);
		// 이미 연결된 그 구글 계정으로 다시 연결하면 연결된 것이다
		assertThat(linkGoogle(login(user, null), first, newEmail(), true)).hasRedirectedUrl(ACCOUNT + "linked");
	}

	@Test
	void deniedConsentWhileLinkingGoesBackToMyAccountStillLoggedIn() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);
		String session = login(user, null);
		assertThat(startLink(session)).hasStatus(HttpStatus.NO_CONTENT);
		MvcTestResult start = toGoogle(session);

		MvcTestResult back = this.mvc.get().uri("/login/oauth2/code/google")
			.param("error", "access_denied")
			.param("state", query(start).get("state"))
			.cookie(new Cookie("SESSION", session))
			.exchange();

		assertThat(back).hasRedirectedUrl(ACCOUNT + "error");
		assertThat(me(session)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "id": %d }
				""".formatted(user));
		// 연결 표시는 돌아오면 지워진다. 다시 구글에 다녀오면 보통의 구글 로그인이다
		assertThat(googleLogin(toGoogle(session), session, newSub(), newEmail(), true, "새 사람")).hasRedirectedUrl(HOME);
		assertThat(identitiesOf(user)).isZero();
	}

	@Test
	void withoutTheCsrfCheckedStartGoogleLogsInAsBefore() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);
		String session = login(user, null);
		// 남의 사이트가 보낸 요청에는 CSRF 토큰이 없다. 로그인하지 않았으면 시작할 수 없다
		assertThat(this.mvc.post().uri("/api/me/google-link").cookie(new Cookie("SESSION", session)))
			.hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code").isEqualTo("CSRF_INVALID");
		assertThat(this.mvc.post().uri("/api/me/google-link").with(xsrf())).hasStatus(HttpStatus.UNAUTHORIZED);
		long other = this.fixtures.user("나래");
		String sub = newSub();
		link(other, sub);

		// 연결을 시작하지 않았으니, 로그인한 채로 구글에 다녀와도 전처럼 그 구글 계정의 사람으로 로그인한다
		MvcTestResult back = googleLogin(toGoogle(session), session, sub, newEmail(), true, "나래");

		assertThat(back).hasRedirectedUrl(HOME);
		assertThat(me(back)).bodyJson().isLenientlyEqualTo("""
				{ "id": %d }
				""".formatted(other));
		assertThat(identitiesOf(user)).isZero();
	}

	@Test
	void anotherLoginInTheSameBrowserDropsAnUnfinishedLink() {
		long first = this.fixtures.passwordUser("가람", PASSWORD, true);
		long second = this.fixtures.passwordUser("나래", PASSWORD, true);
		String session = login(first, null);
		assertThat(startLink(session)).hasStatus(HttpStatus.NO_CONTENT);

		// 가람이 구글에 가지 않고 자리를 떴고, 같은 브라우저에서 나래가 로그인한 뒤 구글로 로그인한다
		String secondSession = login(second, session);
		MvcTestResult back = googleLogin(toGoogle(secondSession), secondSession, newSub(), newEmail(), true, "나래 구글");

		// 나래의 구글 계정이 가람의 계정에 붙지 않는다
		assertThat(back).hasRedirectedUrl(HOME);
		assertThat(identitiesOf(first)).isZero();
	}

	// ── 구글의 생년월일·성별(4.1, 2026-11-16) ─────────────────────────

	@Test
	void aGoogleSignupGetsTheBirthDateAndGenderFromThePeopleApiAndTheConsent() {
		PEOPLE.answer(200, PERSON);
		String email = newEmail();

		assertThat(googleLogin(newSub(), email, true, "가람")).hasRedirectedUrl(HOME);

		long user = userIdOf(email);
		assertThat(profileOf(user)).isEqualTo("1999-03-01|FEMALE");
		// 구글 동의 화면에서 허락한 것을 수집·이용 동의로 남긴다. 이벤트 안내 동의는 받을 화면이 없어 없다
		assertThat(consentsOf(user)).containsExactly(NOW, null);
		// 구글이 준 access token 으로, 허락한 칸만 묻는다
		assertThat(PEOPLE.requests).containsExactly("Bearer fake-access-token personFields=birthdays,genders");
	}

	@Test
	void valuesAlreadyInTheProfileAreNotOverwritten() {
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);
		this.jdbc.sql("insert into profiles (user_id, birth_date) values (:id, date '2000-01-02')").param("id", user).update();
		PEOPLE.answer(200, PERSON);

		// 같은 이메일의 계정에 연결되고, 비어 있던 성별만 채운다
		assertThat(googleLogin(newSub(), this.fixtures.emailOf(user), true, "가람")).hasRedirectedUrl(HOME);
		assertThat(profileOf(user)).isEqualTo("2000-01-02|FEMALE");

		// 다음 구글 로그인도 덮어쓰지 않는다
		PEOPLE.answer(200, """
				{ "birthdays": [ { "date": { "year": 1980, "month": 5, "day": 5 } } ], "genders": [ { "value": "male" } ] }
				""");
		assertThat(googleLogin(googleSubOf(user), this.fixtures.emailOf(user), true, "가람")).hasRedirectedUrl(HOME);
		assertThat(PEOPLE.requests).hasSize(1);
		assertThat(profileOf(user)).isEqualTo("2000-01-02|FEMALE");
	}

	@Test
	void aPeopleApiFailureOrAnUnusableAnswerDoesNotStopTheLogin() {
		PEOPLE.answer(500, "{ \"error\": { \"code\": 500 } }");
		String failed = newEmail();
		MvcTestResult back = googleLogin(newSub(), failed, true, "가람");
		assertThat(back).hasRedirectedUrl(HOME);
		assertThat(me(back)).hasStatusOk();
		assertThat(profileOf(userIdOf(failed))).isNull();

		PEOPLE.answer(200, "not json");
		String unreadable = newEmail();
		assertThat(googleLogin(newSub(), unreadable, true, "나래")).hasRedirectedUrl(HOME);
		assertThat(profileOf(userIdOf(unreadable))).isNull();

		// 연도 없는 생일과 밝히지 않은 성별은 넣지 않는다
		PEOPLE.answer(200, """
				{ "birthdays": [ { "date": { "month": 3, "day": 1 } } ], "genders": [ { "value": "unspecified" } ] }
				""");
		String empty = newEmail();
		assertThat(googleLogin(newSub(), empty, true, "다온")).hasRedirectedUrl(HOME);
		assertThat(profileOf(userIdOf(empty))).isNull();
		// 허락은 했으므로 동의는 남는다
		assertThat(consentsOf(userIdOf(empty))).containsExactly(NOW, null);
	}

	@Test
	void withoutTheBirthdayAndGenderPermissionsThePeopleApiIsNotAsked() {
		// 구글 동의 화면에서 생년월일·성별의 체크를 풀었다
		this.google.grantedScopes = Set.of("openid", "https://www.googleapis.com/auth/userinfo.email",
				"https://www.googleapis.com/auth/userinfo.profile");
		PEOPLE.answer(200, PERSON);
		String email = newEmail();

		assertThat(googleLogin(newSub(), email, true, "가람")).hasRedirectedUrl(HOME);

		assertThat(PEOPLE.requests).isEmpty();
		assertThat(profileOf(userIdOf(email))).isNull();
		assertThat(consentsOf(userIdOf(email))).containsExactly(null, null);

		// 성별만 허락하면 성별만 묻는다
		this.google.grantedScopes = Set.of("openid", GooglePeople.GENDER_SCOPE);
		String genderOnly = newEmail();
		assertThat(googleLogin(newSub(), genderOnly, true, "나래")).hasRedirectedUrl(HOME);
		assertThat(PEOPLE.requests).containsExactly("Bearer fake-access-token personFields=genders");
		assertThat(profileOf(userIdOf(genderOnly))).isEqualTo("-|FEMALE");
	}

	@Test
	void takingOverAnUnverifiedAccountDropsWhatItsSignupLeft() {
		// 누군가 이 이메일로 생년월일·성별과 동의 둘을 넣어 가입만 해 두었다
		String email = newEmail();
		assertThat(this.mvc.post().uri("/api/auth/signup").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "name": "먼저 가입한 사람", "email": "%s", "password": "%s", "birthDate": "1950-01-01", "gender": "male",
					  "birthGenderConsent": true, "eventNoticeConsent": true }
					""".formatted(email, PASSWORD)))
			.hasStatus(HttpStatus.NO_CONTENT);
		PEOPLE.answer(200, PERSON);

		assertThat(googleLogin(newSub(), email, true, "이메일 주인")).hasRedirectedUrl(HOME);

		long user = userIdOf(email);
		assertThat(profileOf(user)).isEqualTo("1999-03-01|FEMALE");
		assertThat(consentsOf(user)).containsExactly(NOW, null);
	}

	@Test
	void linkingFillsTheProfileButAGoogleAccountInUseDoesNot() {
		PEOPLE.answer(200, PERSON);
		long user = this.fixtures.passwordUser("가람", PASSWORD, true);

		assertThat(linkGoogle(login(user, null), newSub(), newEmail(), true)).hasRedirectedUrl(ACCOUNT + "linked");
		assertThat(profileOf(user)).isEqualTo("1999-03-01|FEMALE");
		assertThat(consentsOf(user)).containsExactly(NOW, null);

		long other = this.fixtures.user("나래");
		String sub = newSub();
		link(other, sub);
		long second = this.fixtures.passwordUser("다온", PASSWORD, true);
		PEOPLE.requests.clear();

		// 다른 사람의 구글 계정이라 그 값을 이 사람의 프로필에 넣지 않는다
		assertThat(linkGoogle(login(second, null), sub, newEmail(), true)).hasRedirectedUrl(ACCOUNT + "in-use");
		assertThat(PEOPLE.requests).isEmpty();
		assertThat(profileOf(second)).isNull();
		assertThat(consentsOf(second)).containsExactly(null, null);
	}

	/** 구글로 보낸 뒤, 구글이 이 사람을 확인하고 돌려보낸 것처럼 돌아오는 주소를 부른다. */
	private MvcTestResult googleLogin(String sub, String email, boolean emailVerified, String name) {
		return googleLogin(this.mvc.get().uri("/oauth2/authorization/google").exchange(), sub, email, emailVerified, name);
	}

	/**
	 * start(구글로 보낸 응답)의 세션으로 돌아온다. 브라우저처럼 전에 받은 XSRF-TOKEN 쿠키도 보낸다. 그래서 돌아올 때
	 * CSRF 토큰을 지우고 새로 만드는 일(구글 필터, LoginSession)이 실제처럼 두 번 돈다.
	 */
	private MvcTestResult googleLogin(MvcTestResult start, String sub, String email, boolean emailVerified, String name) {
		return googleLogin(start, setCookie(start, "SESSION"), sub, email, emailVerified, name);
	}

	/** 구글로 보내기 전부터 세션이 있었으면(로그인한 사람) start 는 세션 쿠키를 새로 주지 않는다. 그 세션으로 돌아온다. */
	private MvcTestResult googleLogin(MvcTestResult start, String session, String sub, String email, boolean emailVerified,
			String name) {
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
			.cookie(new Cookie("SESSION", session), new Cookie("XSRF-TOKEN", OLD_XSRF_TOKEN))
			.exchange();
	}

	/** 이메일·비밀번호로 실제 로그인한 세션. session 이 있으면 그 브라우저에서 로그인한다. 로그인이 바꾼 세션 id 를 돌려준다. */
	private String login(long userId, String session) {
		MockMvcTester.MockMvcRequestBuilder request = this.mvc.post().uri("/api/auth/login").with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(this.fixtures.emailOf(userId), PASSWORD));
		if (session != null) {
			request = request.cookie(new Cookie("SESSION", session));
		}
		MvcTestResult login = request.exchange();
		assertThat(login).hasStatusOk();
		return setCookie(login, "SESSION");
	}

	/** 내 정보의 "구글 연결": 연결을 시작하고, 구글로 가서, 구글이 이 사람을 확인하고 돌려보낸다. */
	private MvcTestResult linkGoogle(String session, String sub, String email, boolean emailVerified) {
		assertThat(startLink(session)).hasStatus(HttpStatus.NO_CONTENT);
		return googleLogin(toGoogle(session), session, sub, email, emailVerified, "구글 이름");
	}

	private MvcTestResult startLink(String session) {
		return this.mvc.post().uri("/api/me/google-link").with(xsrf()).cookie(new Cookie("SESSION", session)).exchange();
	}

	private MvcTestResult toGoogle(String session) {
		return this.mvc.get().uri("/oauth2/authorization/google").cookie(new Cookie("SESSION", session)).exchange();
	}

	private MvcTestResult me(String session) {
		return this.mvc.get().uri("/api/me").cookie(new Cookie("SESSION", session)).exchange();
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

	private String googleSubOf(long userId) {
		return this.jdbc.sql("select provider_user_id from user_identities where user_id = :userId").param("userId", userId)
			.query(String.class).single();
	}

	/** 프로필의 "생년월일|성별"(빈 칸은 -). 프로필이 없으면 null. */
	private String profileOf(long userId) {
		return this.jdbc.sql("select coalesce(birth_date::text, '-') || '|' || coalesce(gender, '-') from profiles where user_id = :id")
			.param("id", userId).query(String.class).optional().orElse(null);
	}

	/** 생년월일·성별 수집·이용 동의와 이벤트 안내 동의의 때. 없으면 null. */
	private List<Instant> consentsOf(long userId) {
		return this.jdbc.sql("select birth_gender_consent_at, event_notice_consent_at from users where id = :id")
			.param("id", userId)
			.query((rs, rowNum) -> Arrays.asList(instant(rs.getTimestamp(1)), instant(rs.getTimestamp(2))))
			.single();
	}

	private static Instant instant(Timestamp timestamp) {
		return (timestamp != null) ? timestamp.toInstant() : null;
	}

	private long googleUserOf(String sub) {
		return this.jdbc.sql("select user_id from user_identities where provider_user_id = :sub").param("sub", sub)
			.query(Long.class).single();
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

		/** 사람이 구글 동의 화면에서 허락한 범위. null 이면 요청한 범위를 모두 허락했다. */
		volatile Set<String> grantedScopes;

		@Bean
		OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> fakeTokenEndpoint() {
			return (request) -> OAuth2AccessTokenResponse.withToken("fake-access-token")
				.tokenType(OAuth2AccessToken.TokenType.BEARER)
				.expiresIn(3600)
				// 구글은 토큰 응답의 scope 에 사람이 허락한 범위만 싣는다
				.scopes((this.grantedScopes != null) ? this.grantedScopes : request.getClientRegistration().getScopes())
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

	/**
	 * 내 PC 에 띄운 가짜 People API(127.0.0.1, 빈 포트). {@code GET /v1/people/me} 에 정해 둔 답을 주고, 받은 요청을
	 * "Authorization 머리 쿼리" 꼴로 남긴다.
	 */
	static final class FakePeople {

		final HttpServer server;

		final List<String> requests = new CopyOnWriteArrayList<>();

		private volatile int status;

		private volatile String body;

		FakePeople() {
			try {
				this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
			this.server.createContext("/v1/people/me", this::handle);
			this.server.start();
		}

		String url() {
			return "http://127.0.0.1:" + this.server.getAddress().getPort();
		}

		/** 다음 요청부터 이 답을 준다. 남긴 요청은 지운다. */
		void answer(int status, String body) {
			this.status = status;
			this.body = body;
			this.requests.clear();
		}

		private void handle(HttpExchange exchange) throws IOException {
			this.requests.add(exchange.getRequestHeaders().getFirst("Authorization") + " " + exchange.getRequestURI().getQuery());
			byte[] bytes = this.body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(this.status, bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		}

	}

}
