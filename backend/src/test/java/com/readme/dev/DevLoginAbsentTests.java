package com.readme.dev;

import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.readme.IntegrationTest;

/**
 * dev 프로필이 아니면 임시 로그인과 예시 데이터가 등록되지 않는다.
 */
@IntegrationTest
class DevLoginAbsentTests {

	@Autowired
	ApplicationContext context;

	@Autowired
	MockMvcTester mvc;

	@Test
	void devOnlyBeansAreNotRegistered() {
		assertThat(this.context.getEnvironment().getActiveProfiles()).doesNotContain("dev");
		assertThat(this.context.getBeansOfType(DevLoginController.class)).isEmpty();
		assertThat(this.context.getBeansOfType(DevSeedData.class)).isEmpty();
	}

	@Test
	void devLoginAddressDoesNotExist() {
		assertThat(this.mvc.post().uri("/api/auth/dev-login").with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"email\": \"daon@example.com\" }"))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
	}

}
