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

	/** PUT …/deadline 의 본문. 예: { "date": "2026-11-20" } */
	record FillDeadlineRequest(@NotNull LocalDate date) {
	}

	/**
	 * POST /api/groups/{groupId}/posts 의 본문(설계 4.4 의 올리기 요청). 회사명, 공고명, 마감이 필수다.
	 * 원문 주소는 http·https 만 받는다(설계 6절 12번). 빈 글자는 넣지 않은 것으로 본다.
	 */
	record PostRequest(@NotBlank @Size(max = 200) String company, @NotBlank @Size(max = 300) String title,
			@NotNull @Valid DeadlineInput deadline, @Size(max = 50) String deadlineName,
			@Size(max = 100) String applyAt,
			@Size(max = 2000) @Pattern(regexp = "(?i)(https?://\\S+)?") String sourceUrl) {

		/** 마감 종류와 날짜의 짝이 맞는지: 날짜는 "date"일 때만 있다. */
		boolean deadlineIsConsistent() {
			return (this.deadline.kind() == DeadlineKind.DATE) == (this.deadline.date() != null);
		}

		PostingDraft toDraft() {
			return new PostingDraft(this.company.strip(), this.title.strip(), this.deadline.kind(), this.deadline.date(),
					blankToNull(this.deadlineName), blankToNull(this.applyAt), blankToNull(this.sourceUrl));
		}

		private static String blankToNull(String text) {
			return (text == null || text.isBlank()) ? null : text.strip();
		}

	}

	/** 마감. 프런트의 Deadline 모양 그대로: { "kind": "date", "date": "2026-11-16" }, { "kind": "unknown" }. */
	record DeadlineInput(@NotNull DeadlineKind kind, LocalDate date) {
	}

}
