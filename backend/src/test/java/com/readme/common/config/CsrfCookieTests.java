package com.readme.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * CSRF 쿠키의 도메인과 HttpOnly. 상위 도메인(app.csrf-cookie-domain)은 운영에서만 넣으므로, 통합 테스트(빈 값)로는
 * 이 길이 돌지 않는다. 깨지면 프런트(app.)가 서버(api.)의 쿠키를 읽지 못해 운영의 바꾸는 요청이 모두 403 이 된다.
 */
class CsrfCookieTests {

	@Test
	void inProductionTheCookieGoesToTheParentDomainAndScriptsCanReadIt() {
		Cookie cookie = issueCookie("readme.example");

		assertThat(cookie.getDomain()).isEqualTo("readme.example");
		// 프런트의 스크립트가 읽어 X-XSRF-TOKEN 헤더에 넣으므로 HttpOnly 가 아니다
		assertThat(cookie.isHttpOnly()).isFalse();
		assertThat(cookie.getPath()).isEqualTo("/");
		assertThat(cookie.getValue()).isNotEmpty();
	}

	@Test
	void withoutADomainTheCookieStaysOnTheServerHost() {
		for (String domain : new String[] { null, "" }) {
			Cookie cookie = issueCookie(domain);

			assertThat(cookie.getDomain()).isNull();
			assertThat(cookie.isHttpOnly()).isFalse();
		}
	}

	/** SecurityConfig 의 저장소로 토큰을 새로 만들어 저장하고, 응답에 실린 XSRF-TOKEN 쿠키를 돌려준다. */
	private static Cookie issueCookie(String csrfCookieDomain) {
		SecurityConfig config = new SecurityConfig(
				new AppProperties("https://app.readme.example", csrfCookieDomain, "리드미 <no-reply@readme.example>"),
				JsonMapper.builder().build());
		CsrfTokenRepository repository = config.csrfTokenRepository();
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		repository.saveToken(repository.generateToken(request), request, response);

		Cookie cookie = response.getCookie("XSRF-TOKEN");
		assertThat(cookie).isNotNull();
		return cookie;
	}

}
