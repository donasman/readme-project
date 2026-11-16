package com.readme.inboundmail;

import org.springframework.http.HttpStatus;

import com.readme.common.error.ErrorCode;

enum InboundMailErrorCode implements ErrorCode {

	/** 받는 API: 서명이 틀렸거나, 시각이 5분 넘게 다르거나, 서버에 비밀 값이 없다(메일 설계 4.1). Worker 만 본다. */
	MAIL_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "서명이 맞지 않습니다"),

	/** 받는 API: 원문이 10MB 를 넘는다. */
	MAIL_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "메일이 너무 큽니다"),

	/** 보내는 주소가 이미 10개다. */
	TOO_MANY_SENDERS(HttpStatus.CONFLICT, "보내는 주소는 10개까지 둘 수 있습니다"),

	/** 내 메일이 아니거나, 그 동작을 할 수 없는 메일이다(이미 반영했거나 무시했다, 되돌릴 것이 없다). 둘을 구분하지 않는다. */
	MAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "메일을 찾을 수 없습니다");

	private final HttpStatus status;

	private final String message;

	InboundMailErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	@Override
	public HttpStatus status() {
		return this.status;
	}

	@Override
	public String message() {
		return this.message;
	}

}
