package com.readme.transcript;

import org.springframework.http.HttpStatus;

import com.readme.common.error.ErrorCode;

/** 성적증명서 읽기의 오류(학점 계산기 문서 4절). 파일 쪽은 자료 올리기(extraction 의 ExtractionErrorCode)와 짝이 맞는다. */
enum TranscriptErrorCode implements ErrorCode {

	/** PDF 10MB, 사진 5MB, 스캔한 PDF 20쪽, 읽은 글 3만 자를 넘는다. */
	TRANSCRIPT_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "PDF는 10MB, 사진은 5MB, 스캔한 PDF는 20쪽까지 읽습니다"),

	/** PDF 와 사진(JPG, PNG, GIF, WebP)이 아니다. 형식은 확장자가 아니라 파일의 앞부분으로 판단한다. */
	TRANSCRIPT_UNSUPPORTED_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PDF나 사진(JPG, PNG, GIF, WebP) 파일만 읽습니다"),

	/** 손상되었거나 암호가 걸렸거나 글자를 읽지 못했다. Claude 가 과목 줄을 만들지 못한 때(LLM_FAILED)도 여기다. */
	TRANSCRIPT_UNREADABLE(HttpStatus.UNPROCESSABLE_CONTENT, "파일을 읽지 못했습니다. 손상되었거나 암호가 걸렸거나 글자가 잘 보이지 않는 파일일 수 있습니다"),

	/** 읽었지만 과목 줄이 없다(성적증명서가 아니다). */
	TRANSCRIPT_NO_COURSES(HttpStatus.UNPROCESSABLE_CONTENT, "과목을 찾지 못했습니다. 성적증명서 파일인지 확인해 주세요");

	private final HttpStatus status;

	private final String message;

	TranscriptErrorCode(HttpStatus status, String message) {
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
