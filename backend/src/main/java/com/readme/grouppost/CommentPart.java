package com.readme.grouppost;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 댓글 본문의 한 조각. 글자이거나 링크다. 프런트의 CommentPart 모양이다.
 *
 * @param href 링크일 때만 있다. http·https 주소만 링크가 된다
 */
record CommentPart(String text, @JsonInclude(JsonInclude.Include.NON_NULL) String href) {

	/**
	 * 주소에 쓰는 글자(RFC 3986)만 링크로 잡는다. 그래서 "https://…/review에서"의 "에서"는 글자로 남는다.
	 * 링크는 문장부호(.,;:!?)나 닫는 괄호로 끝나지 않는다: "(https://…/a)."의 ")."는 글자다.
	 * 경로에 한글이 그대로 든 주소는 한글 앞에서 끊긴다. 브라우저가 복사해 주는 주소는 %로 인코딩되어 있어 드물다.
	 */
	private static final Pattern LINK = Pattern
		.compile("https?://[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]*[A-Za-z0-9\\-_~/#=&%+]");

	/**
	 * 저장된 글자를 글자와 링크 조각으로 나눈다. http·https 로 시작하는 주소만 링크로 만들고,
	 * javascript: 같은 다른 주소는 글자로 둔다(설계 6절 13번).
	 */
	static List<CommentPart> split(String body) {
		List<CommentPart> parts = new ArrayList<>();
		Matcher link = LINK.matcher(body);
		int start = 0;
		while (link.find()) {
			if (link.start() > start) {
				parts.add(new CommentPart(body.substring(start, link.start()), null));
			}
			parts.add(new CommentPart(link.group(), link.group()));
			start = link.end();
		}
		if (start < body.length()) {
			parts.add(new CommentPart(body.substring(start), null));
		}
		return parts;
	}

}
