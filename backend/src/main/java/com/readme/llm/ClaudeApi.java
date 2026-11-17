package com.readme.llm;

import java.util.List;

/**
 * Claude 를 한 번 부른다. 실제 구현은 {@link AnthropicClaudeApi}(공식 Java SDK)이고, 통합 테스트는 가짜로 바꾼다
 * (테스트의 FakeClaudeApi). 사용액 기록과 한도는 이 위의 {@link Llm} 이 한다.
 */
interface ClaudeApi {

	/**
	 * 답을 받으면 결과와 시도마다의 토큰을 돌려준다. 답을 못 받으면(키가 없다, API 오류, 연결 실패) 503 LLM_UNAVAILABLE 을 던진다.
	 */
	<T> ClaudeReply<T> send(ClaudeRequest<T> request);

	/**
	 * @param system   지시문. 없으면 null
	 * @param text     사용자 글(자료, 공고 본문 등)
	 * @param document 사용자 글 앞에 넣는 파일(스캔본, 사진). 없으면 null
	 * @param type     답의 모양. 이 record 의 JSON 스키마로 구조화 출력을 받는다
	 */
	record ClaudeRequest<T>(String model, LlmFeature feature, String system, String text, LlmDocument document,
			Class<T> type) {

		ClaudeRequest(String model, LlmFeature feature, String system, String text, Class<T> type) {
			this(model, feature, system, text, null, type);
		}

	}

	/** @param value outcome 이 DONE 일 때만 있다 */
	record ClaudeReply<T>(Outcome outcome, T value, List<LlmAttempt> attempts) {

		static <T> ClaudeReply<T> done(T value, List<LlmAttempt> attempts) {
			return new ClaudeReply<>(Outcome.DONE, value, attempts);
		}

		static <T> ClaudeReply<T> failed(Outcome outcome, List<LlmAttempt> attempts) {
			return new ClaudeReply<>(outcome, null, attempts);
		}

	}

	enum Outcome {

		DONE,

		/** 거절했다(stop_reason refusal). 서버 쪽 fallback 이 있으면 넘겨받은 모델까지 거절한 것이다 */
		REFUSED,

		/** 출력 상한(max_tokens)이나 문맥 창에 걸려 답이 잘렸다 */
		CUT_OFF,

		/** 끝까지 답했는데 요청한 모양으로 읽을 수 없다 */
		UNREADABLE

	}

	/**
	 * 모델 한 번의 시도. 서버 쪽 fallback 이 돌면 거절한 모델과 넘겨받은 모델이 따로 한 줄씩이다. 단가가 달라서다.
	 *
	 * @param cacheWrite5mTokens 5분 캐시에 쓴 입력
	 * @param cacheWrite1hTokens 1시간 캐시에 쓴 입력
	 * @param cacheReadTokens    캐시에서 읽은 입력
	 */
	record LlmAttempt(String model, long inputTokens, long outputTokens, long cacheWrite5mTokens,
			long cacheWrite1hTokens, long cacheReadTokens) {
	}

}
