package com.readme.grouppost;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 댓글 본문은 글자 그대로 저장하고, 응답을 만들 때 http·https 주소만 링크 조각으로 나눈다(설계 6절 13번).
 */
class CommentPartTests {

	@Test
	void plainTextIsOnePart() {
		assertThat(CommentPart.split("포트폴리오 링크 칸이 필수예요."))
			.containsExactly(new CommentPart("포트폴리오 링크 칸이 필수예요.", null));
	}

	@Test
	void httpAndHttpsAddressesBecomeLinks() {
		assertThat(CommentPart.split("후기 https://example.com/review 와 http://example.com/a?b=1 참고"))
			.containsExactly(
					new CommentPart("후기 ", null),
					new CommentPart("https://example.com/review", "https://example.com/review"),
					new CommentPart(" 와 ", null),
					new CommentPart("http://example.com/a?b=1", "http://example.com/a?b=1"),
					new CommentPart(" 참고", null));
	}

	@Test
	void linkAtTheStartOrEndHasNoEmptyText() {
		assertThat(CommentPart.split("https://example.com"))
			.containsExactly(new CommentPart("https://example.com", "https://example.com"));
	}

	@Test
	void koreanRightAfterALinkStaysText() {
		assertThat(CommentPart.split("후기는 https://example.com/review에서 봤어요"))
			.containsExactly(
					new CommentPart("후기는 ", null),
					new CommentPart("https://example.com/review", "https://example.com/review"),
					new CommentPart("에서 봤어요", null));
	}

	@Test
	void closingBracketsAndPunctuationAfterALinkStayText() {
		assertThat(CommentPart.split("(https://example.com/a). 그리고 https://example.com/b?x=1, 끝"))
			.containsExactly(
					new CommentPart("(", null),
					new CommentPart("https://example.com/a", "https://example.com/a"),
					new CommentPart("). 그리고 ", null),
					new CommentPart("https://example.com/b?x=1", "https://example.com/b?x=1"),
					new CommentPart(", 끝", null));
	}

	@Test
	void queryFragmentAndPercentEncodingStayInTheLink() {
		assertThat(CommentPart.split("https://example.com/a?q=%ED%95%9C&b=1#top"))
			.containsExactly(new CommentPart("https://example.com/a?q=%ED%95%9C&b=1#top",
					"https://example.com/a?q=%ED%95%9C&b=1#top"));
	}

	@Test
	void otherSchemesStayText() {
		assertThat(CommentPart.split("javascript:alert(1) 와 ftp://example.com 은 링크가 아니다"))
			.containsExactly(new CommentPart("javascript:alert(1) 와 ftp://example.com 은 링크가 아니다", null));
	}

}
