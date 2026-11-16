package com.readme.inboundmail;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.readme.auth.CurrentUser;
import com.readme.auth.LoginUser;

/** 메일 자동화 설정(메일 설계 4.2). 프런트의 MailSettings 모양이다. */
@RestController
class MailSettingsController {

	private final MailSettingsService service;

	private final InboundMailProperties properties;

	private final JdbcClient jdbc;

	MailSettingsController(MailSettingsService service, InboundMailProperties properties, JdbcClient jdbc) {
		this.service = service;
		this.properties = properties;
		this.jdbc = jdbc;
	}

	/**
	 * @param inboundAddress 내 수신 주소. 서버에 수신 도메인 설정이 없으면 null
	 * @param senders        보내는 주소. 더한 순서
	 */
	record MailSettingsResponse(String inboundAddress, List<String> senders) {
	}

	record SenderRequest(@NotBlank @Email @Size(max = 320) String email) {
	}

	/** 처음 부르면 수신 주소를 만들고 계정 이메일을 보내는 주소에 넣는다. */
	@GetMapping("/api/me/mail-settings")
	MailSettingsResponse settings(@CurrentUser LoginUser user) {
		this.service.ensure(user.id());
		return read(user.id());
	}

	/** 수신 주소를 새로 만든다. */
	@PostMapping("/api/me/mail-settings/inbound-address")
	MailSettingsResponse renew(@CurrentUser LoginUser user) {
		this.service.renew(user.id());
		return read(user.id());
	}

	@PostMapping("/api/me/mail-settings/senders")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void addSender(@Valid @RequestBody SenderRequest request, @CurrentUser LoginUser user) {
		this.service.addSender(user.id(), request.email());
	}

	@DeleteMapping("/api/me/mail-settings/senders")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeSender(@RequestParam String email, @CurrentUser LoginUser user) {
		this.service.removeSender(user.id(), email);
	}

	private MailSettingsResponse read(long userId) {
		String token = this.jdbc.sql("select token from mail_inboxes where user_id = :userId")
			.param("userId", userId)
			.query(String.class)
			.optional()
			.orElse(null);
		List<String> senders = this.jdbc.sql("""
				select email from mail_senders where user_id = :userId order by created_at, email
				""").param("userId", userId).query(String.class).list();
		String address = (token != null && this.properties.hasDomain()) ? token + "@" + this.properties.domain() : null;
		return new MailSettingsResponse(address, senders);
	}

}
