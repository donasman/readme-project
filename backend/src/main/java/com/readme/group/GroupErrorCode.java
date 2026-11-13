package com.readme.group;

import org.springframework.http.HttpStatus;

import com.readme.common.error.ErrorCode;

public enum GroupErrorCode implements ErrorCode {

	/** 그룹이 없거나, 있어도 보는 사람이 멤버가 아니다. 둘을 구분하지 않는다(설계 가정 8). */
	GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "그룹을 찾을 수 없습니다");

	private final HttpStatus status;

	private final String message;

	GroupErrorCode(HttpStatus status, String message) {
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
