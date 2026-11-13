package com.readme.grouppost;

import org.springframework.http.HttpStatus;

import com.readme.common.error.ErrorCode;

enum GroupPostErrorCode implements ErrorCode {

	/** 그 그룹에 없는 공고. 그룹에서 내린 공고도 없는 것과 같다. */
	POST_NOT_FOUND(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다"),

	/** 이미 이 그룹에 있는 공고를 다시 올렸다. 응답에 그 공고의 postId 가 붙는다. */
	ALREADY_IN_GROUP(HttpStatus.CONFLICT, "이미 이 그룹에 올라온 공고입니다"),

	/** 날짜 넣기는 마감일을 읽지 못한 공고에만 한 번 한다. */
	DEADLINE_ALREADY_SET(HttpStatus.CONFLICT, "이미 마감일이 들어간 공고입니다"),

	/** 그 공고에 없는 댓글. */
	COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다");

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
