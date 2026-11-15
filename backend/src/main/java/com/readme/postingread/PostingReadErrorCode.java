package com.readme.postingread;

import org.springframework.http.HttpStatus;

import com.readme.common.error.ErrorCode;

/**
 * 링크나 파일을 읽지 못했을 때. 서버의 고장이 아니라 그 링크·파일의 사정이고, 사용자가 할 일은 본문 붙여넣기다.
 * 링크의 메시지는 이유에 맞게 바꿔 싣는다(PageFetcher). 아래는 기본 메시지다.
 * 파일의 코드는 자료 올리기(extraction 의 ExtractionErrorCode)와 짝이 맞고, 메시지가 붙여넣기를 안내한다(PostingReadController).
 */
enum PostingReadErrorCode implements ErrorCode {

	/** 사이트가 자동으로 읽는 것을 막았다: robots.txt, 401·403·407·429. */
	FETCH_BLOCKED(HttpStatus.UNPROCESSABLE_CONTENT, "이 사이트는 자동으로 읽지 못하게 막아 두었습니다. 공고 본문을 복사해 붙여넣어 주세요"),

	/** 그 밖에 읽지 못했다: 시간 초과, 연결 실패, 웹 페이지가 아님, 너무 큼, 없는 공고, 읽을 수 없는 주소, 공고 본문이 없음. */
	FETCH_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "이 링크는 읽지 못했습니다. 공고 본문을 복사해 붙여넣어 주세요"),

	/** 파일이 20MB 를 넘거나, 압축을 풀면 너무 크거나, 읽은 글이 5만 자(붙여넣은 본문의 상한)를 넘는다. */
	FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일이 너무 큽니다. 파일은 20MB, 글은 5만 자까지 읽습니다. 공고 본문을 복사해 붙여넣어 주세요"),

	/** PDF, DOCX, HWPX, HWP 가 아니다. 형식은 확장자가 아니라 파일의 내용으로 판단한다. */
	FILE_UNSUPPORTED_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PDF, DOCX, HWPX, HWP 파일만 읽습니다. 공고 본문을 복사해 붙여넣어 주세요"),

	/** 형식은 맞는데 읽지 못했다: 손상, 암호, 배포용 한글 문서, 읽는 프로세스의 메모리·시간 초과. */
	FILE_UNREADABLE(HttpStatus.UNPROCESSABLE_CONTENT, "파일을 읽지 못했습니다. 손상되었거나 암호가 걸린 파일일 수 있습니다. 공고 본문을 복사해 붙여넣어 주세요"),

	/** 읽을 글자가 없다: 글자 층이 없는 PDF(스캔본), 사진, 빈 파일. 이런 파일을 LLM 으로 읽는 것은 다음 작업이다. */
	FILE_NO_TEXT(HttpStatus.UNPROCESSABLE_CONTENT, "글자를 읽을 수 없는 파일입니다. 스캔한 파일은 아직 읽지 못합니다. 공고 본문을 복사해 붙여넣어 주세요");

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
