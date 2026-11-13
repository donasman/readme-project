package com.readme.grouppost;

import org.springframework.http.HttpStatus;

import com.readme.common.error.ErrorCode;

enum GroupPostErrorCode implements ErrorCode {

	/** 그 그룹에 없는 공고. 그룹에서 내린 공고도 없는 것과 같다. */
	POST_NOT_FOUND(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다");

	private final HttpStatus status;

	private final String message;

	GroupPostErrorCode(HttpStatus status, String message) {
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
