package com.readme.common.error;

import org.springframework.http.HttpStatus;

/**
 * 어느 기능에도 속하지 않는 오류. 기능의 오류(GROUP_NOT_FOUND 등)는 그 기능 패키지의 enum 에 둔다.
 */
public enum CommonErrorCode implements ErrorCode {

	/** 본문을 읽을 수 없거나, 값이 검사를 통과하지 못했거나, 주소의 값 꼴이 틀렸다. */
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요"),

	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다"),

	/** 로그인은 했지만 이 동작을 할 수 없다. */
	FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다"),

	/** 바꾸는 요청에 X-XSRF-TOKEN 헤더가 없거나 쿠키의 값과 다르다. */
	CSRF_INVALID(HttpStatus.FORBIDDEN, "요청을 확인할 수 없습니다. 화면을 새로고침한 뒤 다시 시도해 주세요"),

	/** 없는 주소. 없는 그룹이나 공고는 기능의 코드(GROUP_NOT_FOUND 등)를 쓴다. */
	NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없습니다"),

	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청입니다"),

	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 생겼습니다. 잠시 뒤 다시 시도해 주세요");

	private final HttpStatus status;

	private final String message;

	CommonErrorCode(HttpStatus status, String message) {
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
