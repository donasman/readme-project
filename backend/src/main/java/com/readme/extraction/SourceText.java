package com.readme.extraction;

import java.io.Writer;
import java.text.Normalizer;

import com.readme.common.error.ApiException;

/**
 * 읽어 낸 글을 저장할 꼴로 다듬는다. 파일에서 읽은 글과 붙여넣은 글이 같은 규칙을 거친다.
 */
final class SourceText {

	/** 본문 글자 수(코드 포인트)의 상한. 조각 200개(LLM 호출 200회)다. 이력서·포트폴리오는 보통 10만 자를 넘지 않는다 */
	static final int MAX_CHARS = 1_000_000;

	/** 읽는 동안 쌓아 두는 글의 상한(UTF-16 글자 수). 압축을 푼 큰 파일이 메모리를 채우지 않게 일찍 멈춘다 */
	static final int READ_LIMIT = 2 * MAX_CHARS;

	private SourceText() {
	}

	/**
	 * 줄바꿈을 \n 으로 맞추고, 제어 문자(탭과 줄바꿈은 남긴다)·NUL·BOM·짝 없는 서로게이트를 지우고, 한글 자모를 NFC 로 합친 뒤
	 * 앞뒤 공백을 지운다. PostgreSQL 의 text 는 NUL 을 받지 않는다. macOS 에서 만든 파일은 한글이 자모로 풀려 있을 수 있다.
	 */
	static String normalize(String raw) {
		String text = raw.replace("\r\n", "\n").replace('\r', '\n');
		StringBuilder kept = new StringBuilder(text.length());
		text.codePoints().filter(SourceText::isKept).forEach(kept::appendCodePoint);
		return Normalizer.normalize(kept, Normalizer.Form.NFC).strip();
	}

	static int length(String text) {
		return text.codePointCount(0, text.length());
	}

	/** 다듬은 글이 비었거나 너무 길면 오류. */
	static void check(String text) {
		if (text.isEmpty()) {
			throw new ApiException(ExtractionErrorCode.SOURCE_EMPTY);
		}
		if (length(text) > MAX_CHARS) {
			throw tooLong();
		}
	}

	static ApiException tooLong() {
		return new ApiException(ExtractionErrorCode.SOURCE_TOO_LARGE, "글이 너무 깁니다. 100만 자까지 읽습니다");
	}

	private static boolean isKept(int codePoint) {
		if (codePoint == '\n' || codePoint == '\t') {
			return true;
		}
		int type = Character.getType(codePoint);
		return type != Character.CONTROL && type != Character.SURROGATE && codePoint != 0xFEFF;
	}

	/** 읽는 동안 글을 쌓는다. READ_LIMIT 를 넘으면 더 읽지 않고 SOURCE_TOO_LARGE 를 던진다. */
	static final class Collector extends Writer {

		private final StringBuilder text = new StringBuilder();

		Collector add(String part) {
			this.text.append(part);
			if (this.text.length() > READ_LIMIT) {
				throw tooLong();
			}
			return this;
		}

		@Override
		public void write(char[] buffer, int offset, int length) {
			add(new String(buffer, offset, length));
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}

		@Override
		public String toString() {
			return this.text.toString();
		}

	}

}
