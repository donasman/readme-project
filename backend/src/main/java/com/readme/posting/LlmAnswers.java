package com.readme.posting;

import java.util.function.Supplier;

import com.readme.common.token.Tokens;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 입력이 같으면 LLM 을 다시 부르지 않고 저장한 답을 다시 쓴다(2026-11-24 사용자 결정). 카드 우선순위와 문항 매칭이 함께 쓴다.
 * 저장은 각 기능이 결과를 저장하는 트랜잭션에서 한다(user_postings 의 key·answer 칸).
 */
final class LlmAnswers {

	private LlmAnswers() {
	}

	/**
	 * 이번 답.
	 *
	 * @param reused 저장한 답을 다시 썼는지
	 * @param key    이번 입력의 key. 저장할 때 쓴다
	 * @param json   답의 JSON. 저장할 때 쓴다
	 */
	record Reply<T>(T answer, boolean reused, String key, String json) {
	}

	/** LLM 입력의 key. 모델, 지시문, 사용자 글이 모두 같아야 같다. */
	static String key(String model, String system, String text) {
		return Tokens.sha256Hex(model + "\0" + system + "\0" + text);
	}

	/**
	 * key 가 저장한 key 와 같고 저장한 답을 읽을 수 있으면 그 답을, 아니면 ask 의 답(한도 검사와 LLM 호출)을 돌려준다.
	 *
	 * @param storedKey  앞에 저장한 key. 없으면 null
	 * @param storedJson 앞에 저장한 답. 없으면 null
	 */
	static <T> Reply<T> reuseOrAsk(JsonMapper jsonMapper, String key, String storedKey, String storedJson, Class<T> type,
			Supplier<T> ask) {
		T stored = key.equals(storedKey) ? stored(jsonMapper, storedJson, type) : null;
		T answer = (stored != null) ? stored : ask.get();
		return new Reply<>(answer, stored != null, key, jsonMapper.writeValueAsString(answer));
	}

	/** 저장한 답. 없거나 지금의 답 모양으로 읽지 못하면 null 이라 LLM 을 다시 부른다. */
	static <T> T stored(JsonMapper jsonMapper, String json, Class<T> type) {
		if (json == null) {
			return null;
		}
		try {
			return jsonMapper.readValue(json, type);
		}
		catch (JacksonException ex) {
			return null;
		}
	}

}
