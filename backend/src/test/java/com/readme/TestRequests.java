package com.readme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.Cookie;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

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

}
