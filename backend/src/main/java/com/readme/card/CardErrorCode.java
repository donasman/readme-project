package com.readme.card;

import org.springframework.http.HttpStatus;

import com.readme.common.error.ErrorCode;

enum CardErrorCode implements ErrorCode {

	/** 카드가 없거나, 있어도 남의 카드다. 둘을 구분하지 않는다. */
	CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "카드를 찾을 수 없습니다");

	private final HttpStatus status;

	private final String message;

	CardErrorCode(HttpStatus status, String message) {
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
