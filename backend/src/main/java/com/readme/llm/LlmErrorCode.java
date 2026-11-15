package com.readme.llm;

import org.springframework.http.HttpStatus;

import com.readme.common.error.ErrorCode;

/**
 * LLM 호출의 오류(경험카드 설계 4.5). LLM 을 쓰는 기능의 API 가 그대로 낸다. 비동기 작업은 이 코드를 작업의 실패로 남긴다.
 */
public enum LlmErrorCode implements ErrorCode {

	/** 이번 달(서울 기준 달력 월) 사용액이 한도에 닿았다. LLM 을 쓰는 기능만 막는다. */
	LLM_LIMIT_REACHED(HttpStatus.TOO_MANY_REQUESTS, "이번 달 무료 사용량을 다 썼습니다. 다음 달 1일부터 다시 쓸 수 있습니다"),

	/** API 키가 없거나 Claude API 를 부르지 못했다(키 오류, 계정 한도·크레딧 소진, 과부하, 연결 실패). 사용액에 넣지 않는다. */
	LLM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 AI 기능을 쓸 수 없습니다. 잠시 뒤 다시 시도해 주세요"),

	/** 답을 받았지만 쓸 수 없다(거절, 출력 상한에 걸림, 형식이 틀림). 사용액에는 넣는다. */
	LLM_FAILED(HttpStatus.BAD_GATEWAY, "AI가 답을 만들지 못했습니다. 다시 시도해 주세요");

	private final HttpStatus status;

	private final String message;

	LlmErrorCode(HttpStatus status, String message) {
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
