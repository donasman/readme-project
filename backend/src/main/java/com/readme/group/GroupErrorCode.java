package com.readme.group;

import org.springframework.http.HttpStatus;

import com.readme.common.error.ErrorCode;

public enum GroupErrorCode implements ErrorCode {

	/** 그룹이 없거나, 있어도 보는 사람이 멤버가 아니다. 둘을 구분하지 않는다(설계 가정 8). */
	GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "그룹을 찾을 수 없습니다"),

	/** 그룹장은 그룹을 나갈 수 없다(설계 가정 7). */
	LEADER_CANNOT_LEAVE(HttpStatus.CONFLICT, "그룹장은 그룹을 나갈 수 없습니다"),

	/** 그런 초대 링크가 없다. 주소가 잘렸거나 잘못 옮겨졌다. 취소할 때는 그 그룹에 그런 초대가 없다. */
	INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "초대 링크를 찾을 수 없습니다. 주소를 다시 확인해 주세요"),

	/** 만료되었거나 취소된 초대 링크. */
	INVITE_EXPIRED(HttpStatus.GONE, "만료된 초대 링크입니다. 새 링크를 받아 주세요");

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
