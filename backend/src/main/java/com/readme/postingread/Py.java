package com.readme.postingread;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * jobfit 이 기대는 Python 문자열 함수 셋. Java 의 같은 이름 함수와 다르게 동작하는 곳이 있어 옮긴 규칙이 그대로 돌게 한다.
 *
 * <ul>
 * <li>{@code str.strip()}: 유니코드 공백(전각 공백, nbsp 포함)을 뗀다. Java 의 {@code strip()}은 nbsp 를 남긴다.</li>
 * <li>{@code str.splitlines()}: \r\n, \r, \v, \f, \x1c~\x1e, \x85,  ,   에서 자르고, 끝의 줄바꿈 뒤에는 빈 줄을 만들지 않는다.</li>
 * <li>{@code len()}: 코드 포인트 수. Java 의 {@code length()}는 이모지를 둘로 센다.</li>
 * </ul>
 */
final class Py {

	private static final Pattern LINE_BREAK = Pattern.compile("\r\n|[\n\r\u000B\f\u001C\u001D\u001E\u0085  ]");

	private Py() {
	}

	static List<String> lines(String text) {
		List<String> lines = new ArrayList<>();
		Matcher m = LINE_BREAK.matcher(text);
		int start = 0;
		while (m.find()) {
			lines.add(text.substring(start, m.start()));
			start = m.end();
		}
		if (start < text.length()) {
			lines.add(text.substring(start));
		}
		return lines;
	}

	static String strip(String text) {
		int start = 0;
		int end = text.length();
		while (start < end && isSpace(text.codePointAt(start))) {
			start += Character.charCount(text.codePointAt(start));
		}
		while (end > start && isSpace(text.codePointBefore(end))) {
			end -= Character.charCount(text.codePointBefore(end));
		}
		return text.substring(start, end);
	}

	/** {@code str.strip(chars)}: 앞뒤에서 chars 에 든 글자를 뗀다. */
	static String strip(String text, String chars) {
		return rstrip(lstrip(text, chars), chars);
	}

	static String lstrip(String text, String chars) {
		int start = 0;
		while (start < text.length() && chars.indexOf(text.charAt(start)) >= 0) {
			start++;
		}
		return text.substring(start);
	}

	static String rstrip(String text, String chars) {
		int end = text.length();
		while (end > 0 && chars.indexOf(text.charAt(end - 1)) >= 0) {
			end--;
		}
		return text.substring(0, end);
	}

	static int len(String text) {
		return text.codePointCount(0, text.length());
	}

	/** {@code str.isspace()}: 공백 구분 글자(Zs, Zl, Zp)와 제어 공백. */
	private static boolean isSpace(int c) {
		return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0x85;
	}

}
