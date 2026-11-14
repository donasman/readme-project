package com.readme.grouppost;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.readme.posting.DeadlineKind;
import com.readme.posting.PostingDraft;

/**
 * 그룹 공고 API 가 받는 본문. 길이 제한은 테이블 칸의 길이와 같다.
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
	 * POST /api/groups/{groupId}/posts 의 본문(설계 4.4 의 올리기 요청). 회사명, 공고명, 마감이 필수다.
	 * 원문 주소는 http·https 만 받는다(설계 6절 12번). 빈 글자는 넣지 않은 것으로 본다.
	 * 역슬래시도 받지 않는다. 브라우저는 \ 를 / 로 읽어 다른 호스트를 여는데, 공고키는 뒤쪽 호스트로 만들어진다.
	 */
	record PostRequest(@NotBlank @Size(max = 200) String company, @NotBlank @Size(max = 300) String title,
			@NotNull @Valid DeadlineInput deadline, @Size(max = 50) String deadlineName,
			@Size(max = 100) String applyAt,
			@Size(max = 2000) @Pattern(regexp = "(?i)(https?://[^\\s\\\\]+)?") String sourceUrl) {

		PostingDraft toDraft() {
			return new PostingDraft(this.company.strip(), this.title.strip(), this.deadline.kind(), this.deadline.date(),
					blankToNull(this.deadlineName), blankToNull(this.applyAt), blankToNull(this.sourceUrl));
		}

		private static String blankToNull(String text) {
			return (text == null || text.isBlank()) ? null : text.strip();
		}

	}

	/**
	 * PUT /api/groups/{groupId}/posts/{postId} 의 본문(공고 고치기, 설계 4.5). 올리기와 같은 칸, 같은 길이다.
	 * 마감은 날짜나 상시만 받는다. 원문 주소와 공고키는 고치지 않는다.
	 */
	record EditRequest(@NotBlank @Size(max = 200) String company, @NotBlank @Size(max = 300) String title,
			@NotNull @Valid DeadlineInput deadline) {
	}

	/**
	 * 마감. 프런트의 Deadline 모양 그대로: { "kind": "date", "date": "2026-11-16" }, { "kind": "always" }, { "kind": "unknown" }.
	 * 올리기 본문의 deadline 이고, PUT …/deadline(날짜 넣기)의 본문 자체다.
	 */
	record DeadlineInput(@NotNull DeadlineKind kind, LocalDate date) {

		/** 종류와 날짜의 짝이 맞는지: 날짜는 "date"일 때만, 꼭 있다. */
		boolean isConsistent() {
			return (this.kind == DeadlineKind.DATE) == (this.date != null);
		}

	}

}
