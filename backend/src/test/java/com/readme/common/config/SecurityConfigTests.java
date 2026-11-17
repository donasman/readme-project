package com.readme.common.config;

import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.readme.IntegrationTest;

/**
 * 보안 필터가 내는 응답도 { code, message } 꼴인지, 로그인 전 요청이 세션을 만들지 않는지 본다.
 */
@IntegrationTest
class SecurityConfigTests {

	@Autowired
	MockMvcTester mvc;

	@Test
	void healthIsOpenWithoutLogin() {
		assertThat(this.mvc.get().uri("/actuator/health")).hasStatusOk();
	}

	@Test
	void requestBeforeLoginGets401WithoutCreatingSession() {
		MvcTestResult result = this.mvc.get().uri("/api/me").exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson().isLenientlyEqualTo("""
				{ "code": "UNAUTHENTICATED", "message": "로그인이 필요합니다" }
				""");
		// 로그인 전 요청마다 세션 행이 쌓이면 안 된다.
		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).noneMatch((cookie) -> cookie.startsWith("SESSION="));
	}

	@Test
	void changeWithoutCsrfTokenGets403CsrfInvalid() {
		assertThat(this.mvc.post().uri("/api/groups").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson().extractingPath("$.code").isEqualTo("CSRF_INVALID");
	}

	@Test
	@WithMockUser
	void unknownAddressGets404NotFound() {
		assertThat(this.mvc.get().uri("/api/no-such-address"))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
	}

	@Test
	void withoutAGoogleClientTheGoogleAddressesAre404() {
		// test 프로필에는 구글 클라이언트 값이 없다. 켠 때는 auth 의 GoogleLoginTests 가 본다.
		for (String uri : new String[] { "/oauth2/authorization/google", "/login/oauth2/code/google?code=x&state=y" }) {
			assertThat(this.mvc.get().uri(uri))
				.hasStatus(HttpStatus.NOT_FOUND)
				.bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
		}
	}

	@Test
	void csrfCookieAndHeaderPassButLoginIsStillRequired() {
		// 프런트처럼 쿠키와 헤더를 같은 값으로 보내면 CSRF 검사는 통과하고, 로그인 검사에서 401 이 된다.
		assertThat(this.mvc.post().uri("/api/groups").with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
	}

	@Test
	@WithMockUser
	void changeToUnknownAddressGets404NotFound() {
		assertThat(this.mvc.post().uri("/api/no-such-address").with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
	}

}
