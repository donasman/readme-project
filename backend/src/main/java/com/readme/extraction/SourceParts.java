package com.readme.extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 자료 본문을 5,000자 조각으로 나눈다. 조각 하나가 LLM 호출 한 번이다(경험카드 설계 3.1 의 total_parts).
 *
 * <p>jobfit 의 ai/exp_extract.py {@code _split} 을 옮겼다. 글자 수는 Python 의 len 처럼 코드 포인트로 센다.
 * 공백도 Python 처럼 유니코드 공백(전각 공백, 줄바꿈 없는 공백 포함)으로 본다.
 */
final class SourceParts {

	/** jobfit 의 SOURCE_PART: 프롬프트 한 장에 넣는 자료 글자 수 */
	static final int SIZE = 5000;

	/** 빈 줄(공백만 있는 줄 포함)이나 줄바꿈 하나. Python re 의 \s 처럼 유니코드 공백을 본다 */
	private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n|\\n", Pattern.UNICODE_CHARACTER_CLASS);

	private SourceParts() {
	}

	static int count(String text) {
		return split(text).size();
	}

	/** 문단 경계에서 SIZE 이하 조각으로. 한 문단이 SIZE 보다 길면 그 문단을 자른다. */
	static List<String> split(String text) {
		text = strip(text);
		if (length(text) <= SIZE) {
			return List.of(text);
		}
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int currentLength = 0;
		for (String paragraph : PARAGRAPH_BREAK.split(text, -1)) {
			int paragraphLength = length(paragraph);
			if (currentLength + paragraphLength + 1 > SIZE && currentLength > 0) {
				parts.add(strip(current.toString()));
				current.setLength(0);
				currentLength = 0;
			}
			while (paragraphLength > SIZE) {
				int cut = paragraph.offsetByCodePoints(0, SIZE);
				parts.add(paragraph.substring(0, cut));
				paragraph = paragraph.substring(cut);
				paragraphLength -= SIZE;
			}
			current.append(paragraph).append('\n');
			currentLength += paragraphLength + 1;
		}
		String last = strip(current.toString());
		if (!last.isEmpty()) {
			parts.add(last);
		}
		return parts;
	}

	private static int length(String text) {
		return text.codePointCount(0, text.length());
	}

	/** Python 의 str.strip(): 유니코드 공백(줄바꿈 없는 공백 포함)을 앞뒤에서 지운다. Java 의 strip() 은 줄바꿈 없는 공백을 남긴다. */
	private static String strip(String text) {
		int start = 0;
		int end = text.length();
		while (start < end && isPythonSpace(text.codePointAt(start))) {
			start += Character.charCount(text.codePointAt(start));
		}
		while (end > start && isPythonSpace(text.codePointBefore(end))) {
			end -= Character.charCount(text.codePointBefore(end));
		}
		return text.substring(start, end);
	}

	private static boolean isPythonSpace(int codePoint) {
		return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0x85;
	}

}
