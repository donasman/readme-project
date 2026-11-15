package com.readme.llm;

/**
 * Claude 에 그대로 넣는 파일 하나(정리 문서 7절 "OCR 대체"). PDF 는 쪽마다 글자와 그림을 함께 읽고, 사진은 그림으로 읽는다.
 *
 * @param mediaType application/pdf, image/jpeg, image/png, image/gif, image/webp 가운데 하나
 */
public record LlmDocument(String mediaType, byte[] bytes) {

	public static final String PDF = "application/pdf";

	public LlmDocument {
		if (!PDF.equals(mediaType) && !mediaType.matches("image/(jpeg|png|gif|webp)")) {
			throw new IllegalArgumentException("Claude 가 받지 않는 형식: " + mediaType);
		}
	}

}
