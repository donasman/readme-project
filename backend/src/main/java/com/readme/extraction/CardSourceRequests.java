package com.readme.extraction;

import jakarta.validation.constraints.NotNull;

/**
 * 자료 API 가 받고 돌려주는 작은 모양들.
 */
final class CardSourceRequests {

	private CardSourceRequests() {
	}

	/** POST /api/card-sources 의 JSON 본문(붙여넣은 글). 비었거나 공백뿐이면 422 SOURCE_EMPTY. */
	record TextSourceRequest(@NotNull String text) {
	}

	/**
	 * POST /api/card-sources 의 응답.
	 *
	 * @param duplicate 이미 올린 자료(본문이 같다)였으면 true. 이때 source 는 그때 저장한 자료다
	 */
	record UploadResponse(CardSourceResponse source, boolean duplicate) {
	}

}
