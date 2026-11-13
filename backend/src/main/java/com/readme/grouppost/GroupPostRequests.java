package com.readme.grouppost;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 그룹 공고 API 가 받는 본문.
 */
final class GroupPostRequests {

	private GroupPostRequests() {
	}

	/** PUT …/interest 의 본문. */
	record InterestRequest(@NotNull Boolean interested) {
	}

	/** POST …/comments 의 본문. 앞뒤 공백은 지우고 저장한다. 길이 제한은 테이블 칸(2,000자)과 같다. */
	record CommentRequest(@NotBlank @Size(max = 2000) String text) {
	}

}
