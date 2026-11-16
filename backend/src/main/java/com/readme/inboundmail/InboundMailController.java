package com.readme.inboundmail;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.readme.common.error.ApiException;

/**
 * Cloudflare Email Worker 가 받은 메일을 넘기는 곳(메일 설계 4.1). 로그인과 CSRF 검사 없이 열고(SecurityConfig), 서명으로 Worker 를
 * 확인한다(메일 설계 M1, 2026-11-16 사용자 결정). 본문은 메일 원문 그대로다.
 */
@RestController
class InboundMailController {

	/** 원문의 상한. Cloudflare 는 25MiB 까지 받지만 채용 메일은 대개 첨부를 빼면 작다. Worker 가 먼저 거절한다 */
	static final int MAX_BYTES = 10 * 1024 * 1024;

	private final MailReceiver receiver;

	private final InboundMailProperties properties;

	private final Clock clock;

	InboundMailController(MailReceiver receiver, InboundMailProperties properties, Clock clock) {
		this.receiver = receiver;
		this.properties = properties;
		this.clock = clock;
	}

	/** 받았거나 받지 않고 버렸으면 204. 서명이 틀리면 401 MAIL_SIGNATURE_INVALID, 10MB 를 넘으면 413 MAIL_TOO_LARGE. */
	@PostMapping("/api/inbound-mail")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void receive(HttpServletRequest request,
			@RequestHeader(name = "X-Readme-Timestamp", required = false) String timestamp,
			@RequestHeader(name = "X-Readme-Envelope-From", required = false) String envelopeFrom,
			@RequestHeader(name = "X-Readme-Envelope-To", required = false) String envelopeTo,
			@RequestHeader(name = "X-Readme-Auth-Results", required = false) String authResults,
			@RequestHeader(name = "X-Readme-Signature", required = false) String signature) throws IOException {
		if (request.getContentLengthLong() > MAX_BYTES) {
			throw new ApiException(InboundMailErrorCode.MAIL_TOO_LARGE);
		}
		byte[] raw;
		try (InputStream in = request.getInputStream()) {
			raw = in.readNBytes(MAX_BYTES + 1);
		}
		if (raw.length > MAX_BYTES) {
			throw new ApiException(InboundMailErrorCode.MAIL_TOO_LARGE);
		}
		if (!MailSignature.verify(this.properties.secret(), timestamp, envelopeFrom, envelopeTo, authResults, raw,
				signature, this.clock.instant())) {
			throw new ApiException(InboundMailErrorCode.MAIL_SIGNATURE_INVALID);
		}
		this.receiver.receive(envelopeFrom, envelopeTo, authResults, raw);
	}

}
