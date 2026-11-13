package com.readme.common.error;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 오류 응답 본문. 모든 API 오류는 이 꼴이다: { "code": "GROUP_NOT_FOUND", "message": "그룹을 찾을 수 없습니다" }
 *
 * <p>오류에 붙은 값(details)은 같은 높이에 펼쳐 싣는다: { "code": "ALREADY_IN_GROUP", "message": "…", "postId": 12 }
 */
public record ApiError(String code, String message, @JsonIgnore Map<String, Object> details) {

	public ApiError(String code, String message) {
		this(code, message, Map.of());
	}

	public static ApiError of(ErrorCode errorCode) {
		return new ApiError(errorCode.name(), errorCode.message());
	}

	@JsonAnyGetter
	public Map<String, Object> detailsForJson() {
		return this.details;
	}

}
