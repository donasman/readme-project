package com.readme.dev;

import static com.readme.TestRequests.setCookie;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

/**
 * dev 프로필의 임시 로그인. 진짜 세션(spring_session 의 행)을 만들고, 그 세션으로 지금 로그인한 사람을 읽는다.
 *
 * <p>dev 프로필을 켜야 하므로 다른 통합 테스트와 서버·DB 를 나눠 쓰지 못하고 따로 띄운다. 예시 데이터도 들어간다.
 *
 * <p>따로 띄우는 김에 실제 내장 서버(RANDOM_PORT)로 띄운다. 가짜 서버(MOCK)로 띄우면 Boot 가 WAR 배포로 보고
 * 세션 쿠키 설정(server.servlet.session.cookie)을 쓰지 않아, 쿠키의 수명·HttpOnly·SameSite 를 확인할 수 없다.
 * 요청은 다른 테스트처럼 MockMvcTester 로 보낸다.
 */
@IntegrationTest
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles({ "test", "dev" })
class DevLoginTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	@Test
	void loginStoresASessionThatTheNextRequestReads() {
		long user = this.fixtures.user("가람");
		int before = sessionRows();

		MvcTestResult login = login(this.fixtures.emailOf(user), null);

		assertThat(login).hasStatusOk().bodyJson().extractingPath("$.name").isEqualTo("가람");
		assertThat(sessionRows()).isEqualTo(before + 1);
		String session = setCookie(login, "SESSION");
		assertThat(session).isNotNull();
		assertThat(this.mvc.get().uri("/api/me").cookie(new Cookie("SESSION", session)))
			.hasStatusOk().bodyJson().extractingPath("$.name").isEqualTo("가람");
	}

	@Test
	void requestsBeforeLoginStoreNoSession() {
		int before = sessionRows();

		assertThat(this.mvc.get().uri("/api/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.get().uri("/api/groups")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(login("nobody@example.test", null)).hasStatus(HttpStatus.NOT_FOUND);

		assertThat(sessionRows()).isEqualTo(before);
	}

	@Test
	void loggingInAsAnotherUserSwitchesTheUserAndRenewsTheSessionId() {
		String first = setCookie(login(this.fixtures.emailOf(this.fixtures.user("가람")), null), "SESSION");

		MvcTestResult again = login(this.fixtures.emailOf(this.fixtures.user("나래")), first);
		String second = setCookie(again, "SESSION");

		assertThat(second).isNotNull().isNotEqualTo(first);
		assertThat(this.mvc.get().uri("/api/me").cookie(new Cookie("SESSION", second)))
			.hasStatusOk().bodyJson().extractingPath("$.name").isEqualTo("나래");
		// 로그인 전의 세션 id 는 더 쓸 수 없다(세션 고정 공격 막기)
		assertThat(this.mvc.get().uri("/api/me").cookie(new Cookie("SESSION", first))).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void loginGivesANewCsrfToken() {
		MvcTestResult login = login(this.fixtures.emailOf(this.fixtures.user("가람")), null);

		assertThat(setCookie(login, "XSRF-TOKEN")).isNotNull().isNotEqualTo("test-xsrf-token");
	}

	@Test
	void theSessionCookieOutlivesTheIdleLimitSoOnlyIdlenessLogsOut() {
		MvcTestResult login = login(this.fixtures.emailOf(this.fixtures.user("가람")), null);
		MockCookie cookie = (MockCookie) Arrays.stream(login.getResponse().getCookies())
			.filter((c) -> c.getName().equals("SESSION") && !c.getValue().isEmpty())
			.reduce((first, last) -> last)
			.orElseThrow();
		String sessionId = new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
		long idleLimit = this.jdbc.sql("select max_inactive_interval from spring_session where session_id = :id")
			.param("id", sessionId)
			.query(Long.class)
			.single();

		// 쿠키는 로그인할 때 한 번만 내려온다. 쿠키 수명이 서버의 유휴 제한(마지막 사용 후)만큼이면, 매일 써도
		// 로그인하고 그만큼 지나면 브라우저가 쿠키를 지워 끊긴다. 그래서 쿠키는 길게 두고, 끝나는 때는 유휴 제한이 정한다.
		assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(365).toSeconds());
		assertThat(idleLimit).isLessThan(cookie.getMaxAge());
		// 화면의 스크립트가 읽지 못하고, 다른 사이트에서 보낸 요청(링크 이동 말고)에는 실리지 않는다
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.getSameSite()).isEqualTo("Lax");
	}

	@Test
	void seedDataIsThereForTheExampleUsers() {
		String session = setCookie(login(DevSeedData.MARKER_EMAIL, null), "SESSION");

		assertThat(this.mvc.get().uri("/api/groups").cookie(new Cookie("SESSION", session))).hasStatusOk()
			.bodyJson().extractingPath("$[*].name").asArray().containsExactly("백엔드 스터디", "공기업 전산직", "MES 준비반");
	}

	private MvcTestResult login(String email, String session) {
		MockMvcTester.MockMvcRequestBuilder request = this.mvc.post().uri("/api/auth/dev-login")
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"email\": \"%s\" }".formatted(email));
		if (session != null) {
			request = request.cookie(new Cookie("SESSION", session));
		}
		return request.exchange();
	}

	private int sessionRows() {
		return this.jdbc.sql("select count(*) from spring_session").query(Integer.class).single();
	}

}
