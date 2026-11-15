package com.readme.grouppost;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.readme.posting.DeadlineInput;

/**
 * 그룹 공고 API 가 받는 본문. 길이 제한은 테이블 칸의 길이와 같다. 올리기의 본문은 내 공고 넣기와 같이 쓰므로
 * posting 의 {@link com.readme.posting.UploadRequest} 다. 마감은 posting 의 {@link DeadlineInput} 이다.
 */
final class GroupPostRequests {

	private GroupPostRequests() {
	}

	/** PUT …/interest 의 본문. */
	record InterestRequest(@NotNull Boolean interested) {
	}

	/** POST …/comments, PUT …/comments/{commentId} 의 본문. 앞뒤 공백은 지우고 저장한다. */
	record CommentRequest(@NotBlank @Size(max = 2000) String text) {
	}

	/**
	 * PUT /api/groups/{groupId}/posts/{postId} 의 본문(공고 고치기, 설계 4.5). 올리기와 같은 칸, 같은 길이다.
	 * 마감의 unknown 은 아직 unknown 인 공고에만 받는다(서비스가 본다). 원문 주소와 공고키는 고치지 않는다.
	 */
	record EditRequest(@NotBlank @Size(max = 200) String company, @NotBlank @Size(max = 300) String title,
			@NotNull @Valid DeadlineInput deadline) {
	}

}
