package com.readme.extraction;

import org.springframework.http.HttpStatus;

import com.readme.common.error.ErrorCode;

/**
 * 자료 올리기의 오류(경험카드 설계 4.2). 표는 설계 문서의 "만들면서 정한 것"에 있다.
 */
enum ExtractionErrorCode implements ErrorCode {

	/** 내 자료가 아니거나 없다. 둘을 구분하지 않는다. */
	SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "자료를 찾을 수 없습니다"),

	/** 파일이 20MB 를 넘는다. 읽어 낸 글이 너무 길거나 압축을 풀면 너무 큰 파일도 이 코드에 상황에 맞는 메시지를 붙인다. */
	SOURCE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "20MB까지 올릴 수 있습니다"),

	/** 받는 형식이 아니다. 형식은 확장자가 아니라 파일의 앞부분과 안의 구성으로 판단한다. */
	SOURCE_UNSUPPORTED_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PDF, DOCX, HWPX, HWP, PPTX, TXT, MD 파일만 올릴 수 있습니다"),

	/** 형식은 맞는데 읽다가 실패했다(손상, 암호, 배포용 한글 문서). */
	SOURCE_UNREADABLE(HttpStatus.UNPROCESSABLE_CONTENT, "파일을 읽지 못했습니다. 파일이 손상되었거나 암호가 걸려 있지 않은지 확인해 주세요"),

	/** 글자 층이 없는 PDF(스캔본)나 사진. 글자를 읽어 내는 방법(OCR)은 뒤에 붙인다. */
	SOURCE_NO_TEXT(HttpStatus.UNPROCESSABLE_CONTENT, "글자를 읽을 수 없는 파일입니다. 스캔한 파일과 사진은 아직 읽지 못하고, 곧 읽을 수 있게 할 예정입니다"),

	/** 읽은 글이 비었다(빈 파일, 공백만 붙여넣은 글). */
	SOURCE_EMPTY(HttpStatus.UNPROCESSABLE_CONTENT, "읽을 글이 없습니다");

	private final HttpStatus status;

	private final String message;

	ExtractionErrorCode(HttpStatus status, String message) {
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
