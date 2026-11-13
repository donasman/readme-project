package com.readme.posting;

import java.time.LocalDate;

/**
 * 공고를 올릴 때 사람이 넣은 값. 빈 글자는 null 로 받는다.
 *
 * @param deadlineDate DATE 일 때만 있다
 * @param sourceUrl    원문 주소. http·https 만
 */
public record PostingDraft(String company, String title, DeadlineKind deadlineKind, LocalDate deadlineDate,
		String deadlineName, String applyAt, String sourceUrl) {

	/** 같은 공고를 알아보는 키(jobfit 공고키). 링크가 있으면 링크에서, 없으면 회사명·공고명·마감일에서 만든다. */
	PostingKey key() {
		String deadline = (this.deadlineDate != null) ? this.deadlineDate.toString() : "";
		return PostingKeys.derive(this.sourceUrl, this.company, this.title, deadline);
	}

}
