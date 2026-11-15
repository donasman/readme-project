package com.readme.extraction;

import java.text.Normalizer;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 저장할 자료 한 건: 읽어서 다듬은 글과 파일 정보. 파일과 붙여넣은 글이 여기서 같은 꼴이 된다.
 *
 * @param fileName    파일 이름(경로를 떼고 255자까지). 붙여넣은 글이면 null
 * @param contentType 서버가 알아낸 형식(브라우저가 보낸 값이 아니다). 붙여넣은 글이면 null
 * @param byteSize    올린 파일의 바이트 수. 붙여넣은 글이면 null
 * @param text        다듬은 본문. 비지 않았고 SourceText.MAX_CHARS 이하다
 */
record SourceInput(Kind kind, String fileName, String contentType, Long byteSize, String text) {

	/** 올릴 수 있는 파일 크기. multipart 의 max-file-size(application.yml)와 같다 */
	static final long MAX_BYTES = 20L * 1024 * 1024;

	/** card_sources.file_name 의 길이 */
	private static final int MAX_NAME_LENGTH = 255;

	enum Kind {

		FILE, TEXT;

		/** 응답에 쓰는 값: "file", "text". */
		@JsonValue
		public String json() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	/** 올린 파일을 읽는다(크기는 UploadedFiles.read 가 먼저 본다). 받지 않는 형식이거나 읽을 글이 없으면 ApiException. */
	static SourceInput file(String originalName, byte[] bytes) {
		String fileName = cleanName(originalName);
		SourceFiles.Read read = SourceFiles.read(fileName, bytes);
		String text = SourceText.normalize(read.text());
		SourceText.check(text);
		return new SourceInput(Kind.FILE, fileName, read.format().contentType, (long) bytes.length, text);
	}

	/** 붙여넣은 글. 파일과 같은 규칙으로 다듬는다. 다듬기 전에 READ_LIMIT 를 넘으면 사본을 만들기 전에 거부한다. */
	static SourceInput text(String pasted) {
		if (pasted.length() > SourceText.READ_LIMIT) {
			throw SourceText.tooLong();
		}
		String text = SourceText.normalize(pasted);
		SourceText.check(text);
		return new SourceInput(Kind.TEXT, null, null, null, text);
	}

	int chars() {
		return SourceText.length(this.text);
	}

	/**
	 * 브라우저가 보낸 파일 이름에서 경로(옛 브라우저는 C:\…\이력서.pdf 를 보낸다)와 제어 문자를 지우고 NFC 로 합친다
	 * (macOS 는 한글 파일 이름을 자모로 풀어 보낸다). 255자를 넘으면 자른다. 남는 것이 없으면 null.
	 */
	static String cleanName(String originalName) {
		if (originalName == null) {
			return null;
		}
		String name = originalName.substring(Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\')) + 1);
		StringBuilder kept = new StringBuilder();
		name.codePoints().filter((c) -> !Character.isISOControl(c) && Character.getType(c) != Character.SURROGATE)
			.forEach(kept::appendCodePoint);
		name = Normalizer.normalize(kept, Normalizer.Form.NFC).strip();
		if (name.isEmpty()) {
			return null;
		}
		return (SourceText.length(name) > MAX_NAME_LENGTH) ? name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH))
				: name;
	}

}
