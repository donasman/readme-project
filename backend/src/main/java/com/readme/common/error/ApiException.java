package com.readme.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 서비스와 컨트롤러가 정해진 오류 응답을 낼 때 던진다. {@link GlobalExceptionHandler} 가 { code, message } 로 바꾼다.
 *
 * <pre>throw new ApiException(GroupErrorCode.GROUP_NOT_FOUND);</pre>
 *
 * <p>프런트가 오류와 함께 받아야 하는 값이 있으면 {@link #with} 로 붙인다. 응답 본문에 같은 이름으로 실린다.
 *
 * <pre>throw new ApiException(GroupPostErrorCode.ALREADY_IN_GROUP).with("postId", postingId);</pre>
 */
public class ApiException extends RuntimeException {

	private final ErrorCode errorCode;

	private final Map<String, Object> details = new LinkedHashMap<>();

	public ApiException(ErrorCode errorCode) {
		this(errorCode, errorCode.message());
	}

	/** 기본 메시지 대신 상황에 맞는 메시지를 줄 때. */
	public ApiException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	/** 응답 본문에 값을 하나 더 싣는다. code, message 와 겹치는 이름은 쓰지 않는다. */
	public ApiException with(String name, Object value) {
		this.details.put(name, value);
		return this;
	}

	public ErrorCode errorCode() {
		return this.errorCode;
	}

	public Map<String, Object> details() {
		return Collections.unmodifiableMap(this.details);
	}

}
