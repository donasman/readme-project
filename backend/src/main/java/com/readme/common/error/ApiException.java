package com.readme.common.error;

/**
 * 서비스와 컨트롤러가 정해진 오류 응답을 낼 때 던진다. {@link GlobalExceptionHandler} 가 { code, message } 로 바꾼다.
 *
 * <pre>throw new ApiException(GroupErrorCode.GROUP_NOT_FOUND);</pre>
 */
public class ApiException extends RuntimeException {

	private final ErrorCode errorCode;

	public ApiException(ErrorCode errorCode) {
		this(errorCode, errorCode.message());
	}

	/** 기본 메시지 대신 상황에 맞는 메시지를 줄 때. */
	public ApiException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return this.errorCode;
	}

}
