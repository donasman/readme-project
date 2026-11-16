package com.readme.inboundmail;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.readme.Fixtures;
import com.readme.IntegrationTest;

import static com.readme.TestRequests.loginAs;
import static com.readme.TestRequests.xsrf;
import static org.assertj.core.api.Assertions.assertThat;

/** 메일 자동화 설정(메일 설계 4.2). */
@IntegrationTest
class MailSettingsTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Fixtures fixtures;

	@Test
	void firstReadMakesTheAddressAndRegistersTheAccountEmail() {
		long me = this.fixtures.user("다온");

		MvcTestResult first = settings(me);
		assertThat(first).hasStatusOk();
		String address = JsonPath.read(body(first), "$.inboundAddress");
		assertThat(address).matches("[a-z0-9]{16}@inbound\\.readme\\.test");
		assertThat(first).bodyJson().extractingPath("$.senders").isEqualTo(List.of(this.fixtures.emailOf(me)));

		assertThat(settings(me)).bodyJson().extractingPath("$.inboundAddress").isEqualTo(address);
	}

	@Test
	void renewMakesANewAddress() {
		long me = this.fixtures.user("다온");
		String before = JsonPath.read(body(settings(me)), "$.inboundAddress");

		MvcTestResult renewed = this.mvc.post()
			.uri("/api/me/mail-settings/inbound-address")
			.with(loginAs(me))
			.with(xsrf())
			.exchange();

		assertThat(renewed).hasStatusOk();
		String after = JsonPath.read(body(renewed), "$.inboundAddress");
		assertThat(after).isNotEqualTo(before).matches("[a-z0-9]{16}@inbound\\.readme\\.test");
	}

	@Test
	void sendersAreAddedOnceInLowerCaseAndRemoved() {
		long me = this.fixtures.user("다온");
		settings(me);

		assertThat(addSender(me, "Name@Gmail.com")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(addSender(me, "NAME@gmail.com")).hasStatus(HttpStatus.NO_CONTENT); // 대소문자만 다른 같은 주소
		assertThat(settings(me)).bodyJson()
			.extractingPath("$.senders")
			.isEqualTo(List.of(this.fixtures.emailOf(me), "name@gmail.com"));

		assertThat(this.mvc.delete()
			.uri("/api/me/mail-settings/senders?email={email}", "NAME@gmail.com")
			.with(loginAs(me))
			.with(xsrf())).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.mvc.delete()
			.uri("/api/me/mail-settings/senders?email={email}", "nobody@gmail.com")
			.with(loginAs(me))
			.with(xsrf())).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(settings(me)).bodyJson().extractingPath("$.senders").isEqualTo(List.of(this.fixtures.emailOf(me)));
	}

	@Test
	void invalidSenderIs400AndTheEleventhIs409() {
		long me = this.fixtures.user("다온");
		settings(me);

		assertThat(addSender(me, "not-an-email")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("INVALID_INPUT");
		for (int i = 1; i < MailSettingsService.MAX_SENDERS; i++) {
			assertThat(addSender(me, "s" + i + "@example.com")).hasStatus(HttpStatus.NO_CONTENT);
		}
		assertThat(addSender(me, "s99@example.com")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("TOO_MANY_SENDERS");
		assertThat(addSender(me, "s1@example.com")).hasStatus(HttpStatus.NO_CONTENT); // 이미 있는 주소는 그대로
	}

	@Test
	void loginIsRequired() {
		assertThat(this.mvc.get().uri("/api/me/mail-settings")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.get().uri("/api/me/mailbox")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.get().uri("/api/me/schedules")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MvcTestResult settings(long userId) {
		return this.mvc.get().uri("/api/me/mail-settings").with(loginAs(userId)).exchange();
	}

	private MvcTestResult addSender(long userId, String email) {
		return this.mvc.post()
			.uri("/api/me/mail-settings/senders")
			.with(loginAs(userId))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"email\": \"" + email + "\" }")
			.exchange();
	}

	private static String body(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
