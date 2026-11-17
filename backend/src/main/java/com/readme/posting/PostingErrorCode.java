package com.readme.posting;

import org.springframework.http.HttpStatus;

import com.readme.common.error.ErrorCode;

enum PostingErrorCode implements ErrorCode {

	/** 없는 공고이거나, 내 그룹에도 내 공고에도 없어 볼 수 없는 공고다. 둘을 구분하지 않는다. */
	POSTING_NOT_FOUND(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다"),

	/** 그룹 없이 내 공고에 넣기: 이미 내 공고에 있는 공고다. 응답에 그 공고의 postingId 가 붙는다. */
	ALREADY_IN_MY_POSTINGS(HttpStatus.CONFLICT, "이미 내 공고에 있는 공고입니다");

	private final HttpStatus status;

	private final String message;

	PostingErrorCode(HttpStatus status, String message) {
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
