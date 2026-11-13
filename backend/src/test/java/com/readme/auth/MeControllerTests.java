package com.readme.auth;

import static com.readme.TestRequests.loginAs;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

@IntegrationTest
class MeControllerTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Autowired
	JdbcClient jdbc;

	@Test
	void myAccount() {
		long me = this.fixtures.user("다온");

		assertThat(this.mvc.get().uri("/api/me").with(loginAs(me))).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{ "id": %d, "name": "다온", "email": "%s", "hasPassword": false, "providers": [] }
				""".formatted(me, this.fixtures.emailOf(me)));
	}

	@Test
	void passwordAndConnectedProviders() {
		long me = this.fixtures.user("가람");
		this.jdbc.sql("update users set password_hash = 'hash' where id = :id").param("id", me).update();
		this.jdbc.sql("insert into user_identities (user_id, provider, provider_user_id) values (:id, 'GOOGLE', :sub)")
			.param("id", me)
			.param("sub", "sub-" + me)
			.update();

		assertThat(this.mvc.get().uri("/api/me").with(loginAs(me))).hasStatusOk()
			.bodyJson().isLenientlyEqualTo("""
					{ "hasPassword": true, "providers": ["google"] }
					""");
	}

	@Test
	void sessionOfADeletedAccountIs401() {
		long gone = this.fixtures.user("탈퇴");
		this.fixtures.deleteUser(gone);

		assertThat(this.mvc.get().uri("/api/me").with(loginAs(gone))).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
	}

	@Test
	void beforeLoginIs401() {
		assertThat(this.mvc.get().uri("/api/me")).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
	}

}
