package com.readme.postingread;

import org.springframework.http.HttpStatus;

import com.readme.common.error.ErrorCode;

/**
 * 링크를 읽지 못했을 때. 둘 다 422 다: 서버의 고장이 아니라 그 링크의 사정이고, 사용자가 할 일은 본문 붙여넣기다.
 * 메시지는 이유에 맞게 바꿔 싣는다(PageFetcher). 아래는 기본 메시지다.
 */
enum PostingReadErrorCode implements ErrorCode {

	/** 사이트가 자동으로 읽는 것을 막았다: robots.txt, 401·403·407·429. */
	FETCH_BLOCKED(HttpStatus.UNPROCESSABLE_CONTENT, "이 사이트는 자동으로 읽지 못하게 막아 두었습니다. 공고 본문을 복사해 붙여넣어 주세요"),

	/** 그 밖에 읽지 못했다: 시간 초과, 연결 실패, 웹 페이지가 아님, 너무 큼, 없는 공고, 읽을 수 없는 주소, 공고 본문이 없음. */
	FETCH_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "이 링크는 읽지 못했습니다. 공고 본문을 복사해 붙여넣어 주세요");

	private final HttpStatus status;

	private final String message;

	PostingReadErrorCode(HttpStatus status, String message) {
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
