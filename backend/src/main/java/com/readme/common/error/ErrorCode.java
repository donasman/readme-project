package com.readme.common.error;

import org.springframework.http.HttpStatus;

/**
 * API 오류 응답 { code, message } 의 code 하나.
 *
 * <p>기능마다 이 인터페이스를 구현한 enum 을 그 기능 패키지에 둔다(예: group 의 GroupErrorCode).
 * 여러 기능이 함께 쓰는 것은 {@link CommonErrorCode} 에 있다. enum 상수 이름이 그대로 응답의 code 가 된다.
 */
public interface ErrorCode {

	/** 응답의 code. enum 이면 상수 이름이다. */
	String name();

	HttpStatus status();

	/** 기본 메시지. 화면에 그대로 보일 수 있으므로 사용자에게 하는 말로 쓴다. */
	String message();

}
