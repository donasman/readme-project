package com.readme.posting;

import org.springframework.http.HttpStatus;

import com.readme.common.error.ErrorCode;

enum PostingErrorCode implements ErrorCode {

	/** 없는 공고이거나, 내 그룹에도 내 공고에도 없어 볼 수 없는 공고다. 둘을 구분하지 않는다. */
	POSTING_NOT_FOUND(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다"),

	/** 그룹 없이 내 공고에 넣기: 이미 내 공고에 있는 공고다. 응답에 그 공고의 postingId 가 붙는다. */
	ALREADY_IN_MY_POSTINGS(HttpStatus.CONFLICT, "이미 내 공고에 있는 공고입니다"),

	/** 카드 우선순위 초안, 문항 매칭 고르기: 내 경험카드가 하나도 없다 */
	NO_CARDS(HttpStatus.CONFLICT, "경험카드가 없습니다. 카드를 먼저 만들어 주세요"),

	/** 카드 우선순위 초안: 공고 분석도 공고 내용 묶음도 없어 LLM 에 보낼 글이 없다 */
	NO_POSTING_CONTENT(HttpStatus.CONFLICT, "공고 내용이 없어 우선순위를 만들 수 없습니다"),

	/** 문항 매칭 카드 바꾸기: 이 공고의 내 문항이 아니다 */
	QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "문항을 찾을 수 없습니다"),

	/** 문항 매칭 고르기: 문항이 하나도 없다 */
	NO_QUESTIONS(HttpStatus.CONFLICT, "자기소개서 문항이 없어 카드를 고를 수 없습니다. 문항을 먼저 넣어 주세요");

	private final HttpStatus status;

	private final String message;

	PostingErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	@Override
	public HttpStatus status() {
		return this.status;
	}

	@Override
	public String message() {
		return this.message;
	}

}
