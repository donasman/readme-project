package com.readme.posting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 공고를 올리는 본문. POST /api/groups/{groupId}/posts(그룹에 올리기, 설계 4.4)와 POST /api/me/postings(그룹 없이 내 공고에 넣기,
 * 설계 4.2)가 같이 쓴다. 회사명, 공고명, 마감이 필수다. 길이 제한은 테이블 칸의 길이와 같다.
 * 원문 주소는 http·https 만 받는다(설계 6절 12번). 빈 글자는 넣지 않은 것으로 본다.
 * 역슬래시도 받지 않는다. 브라우저는 \ 를 / 로 읽어 다른 호스트를 여는데, 공고키는 뒤쪽 호스트로 만들어진다.
 */
public record UploadRequest(@NotBlank @Size(max = 200) String company, @NotBlank @Size(max = 300) String title,
		@NotNull @Valid DeadlineInput deadline, @Size(max = 50) String deadlineName, @Size(max = 100) String applyAt,
		@Size(max = 2000) @Pattern(regexp = "(?i)(https?://[^\\s\\\\]+)?") String sourceUrl) {

	/** 저장할 값. 앞뒤 공백을 지운다. 마감의 짝이 맞지 않으면(날짜 없는 date, 날짜 있는 always·unknown) 400 INVALID_INPUT. */
	public PostingDraft toDraft() {
		DeadlineInput checked = this.deadline.requireConsistent();
		return new PostingDraft(this.company.strip(), this.title.strip(), checked.kind(), checked.date(),
				blankToNull(this.deadlineName), blankToNull(this.applyAt), blankToNull(this.sourceUrl));
	}

	private static String blankToNull(String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

}
