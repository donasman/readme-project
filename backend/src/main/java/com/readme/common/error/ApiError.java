package com.readme.common.error;

/**
 * 오류 응답 본문. 모든 API 오류는 이 꼴이다: { "code": "GROUP_NOT_FOUND", "message": "그룹을 찾을 수 없습니다" }
 */
public record ApiError(String code, String message) {

	public static ApiError of(ErrorCode errorCode) {
		return new ApiError(errorCode.name(), errorCode.message());
	}

}
