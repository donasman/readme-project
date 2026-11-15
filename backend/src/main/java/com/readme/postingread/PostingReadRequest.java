package com.readme.postingread;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/postings/read 의 본문. 링크(url)와 붙여넣은 본문(text) 가운데 하나만 넣는다. 둘 다이거나 둘 다 없으면 400 이다.
 * 주소는 올리기의 원문 주소와 같은 규칙이다: http·https 만, 공백과 역슬래시 없이, 2,000자까지(구조 문서 5.1).
 *
 * @param url       공고 링크. 서버가 그 페이지를 받아 읽는다
 * @param text      붙여넣은 공고 본문. 50,000자까지
 * @param sourceUrl 본문의 원문 링크. text 와 함께만. 비어 있으면 없는 것으로 본다
 * @param groupId   올릴 그룹. 있으면 그 그룹의 멤버만 부를 수 있고, 링크의 공고가 이미 그 그룹에 있으면 알려 준다
 */
record PostingReadRequest(@Size(max = 2000) @Pattern(regexp = "(?i)https?://[^\\s\\\\]+") String url,
		@Size(max = PostingReadRequest.MAX_TEXT) String text,
		@Size(max = 2000) @Pattern(regexp = "(?i)(https?://[^\\s\\\\]+)?") String sourceUrl, Long groupId) {

	/** 읽는 본문의 글자 수(UTF-16) 상한. 파일에서 읽은 글도 같다(PostingReadController) */
	static final int MAX_TEXT = 50_000;

	boolean hasText() {
		return this.text != null && !this.text.isBlank();
	}

	boolean hasSourceUrl() {
		return this.sourceUrl != null && !this.sourceUrl.isBlank();
	}

}
