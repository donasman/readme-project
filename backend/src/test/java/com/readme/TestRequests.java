package com.readme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.Cookie;

import org.springframework.http.HttpHeaders;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.readme.auth.LoginSession;

/**
 * 통합 테스트의 요청에 붙이는 것들.
 */
public final class TestRequests {

	private static final String XSRF_TOKEN = "test-xsrf-token";

	private TestRequests() {
	}

	/**
	 * 프런트가 보내는 그대로 XSRF-TOKEN 쿠키와 X-XSRF-TOKEN 헤더를 같은 값으로 붙인다. 바꾸는 요청(POST, PUT, DELETE)에 쓴다.
	 *
	 * <p>spring-security-test 의 csrf() 는 쓰지 않는다. 처음 부를 때 CSRF 토큰 저장소를 세션 방식으로 바꿔 끼우고,
	 * 그 뒤로는 같은 서버를 쓰는 다른 테스트의 요청도 세션을 만든다. 실제 쿠키 방식도 확인하지 못한다.
	 */
	public static RequestPostProcessor xsrf() {
		return (request) -> {
			List<Cookie> cookies = new ArrayList<>();
			if (request.getCookies() != null) {
				cookies.addAll(Arrays.asList(request.getCookies()));
			}
			cookies.add(new Cookie("XSRF-TOKEN", XSRF_TOKEN));
			request.setCookies(cookies.toArray(Cookie[]::new));
			request.addHeader("X-XSRF-TOKEN", XSRF_TOKEN);
			return request;
		};
	}

	/**
	 * 이 사용자로 로그인한 요청으로 만든다. 실제 로그인(LoginSession)이 세션에 넣는 것과 같은 로그인 정보를 쓴다.
	 * 세션을 실제로 만들고 읽는 것까지 확인하려면 실제 로그인(POST /api/auth/login)을 쓴다(auth/LoginTests).
	 */
	public static RequestPostProcessor loginAs(long userId) {
		return SecurityMockMvcRequestPostProcessors.authentication(LoginSession.authenticationOf(userId));
	}

	/** 응답의 Set-Cookie 에서 이 이름의 마지막 값. 지우는 쿠키(빈 값)는 건너뛴다. 없으면 null. */
	public static String setCookie(MvcTestResult result, String name) {
		String found = null;
		for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
			if (header.startsWith(name + "=")) {
				int end = header.indexOf(';');
				String value = header.substring(name.length() + 1, (end < 0) ? header.length() : end);
				if (!value.isEmpty()) {
					found = value;
				}
			}
		}
		return found;
	}

}
