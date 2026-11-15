package com.readme.llm;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.llm.* 값.
 *
 * @param apiKey          Claude API 키. 개발은 backend/application-local.yml, 운영은 ANTHROPIC_API_KEY. 비어 있으면 서버는 뜨고
 *                        LLM 을 쓰는 기능만 503 LLM_UNAVAILABLE 이다. SDK 의 fromEnv() 를 쓰지 않는다(PC 의 다른 로그인 정보를 찾아 쓴다)
 * @param model           모든 기능이 쓰는 모델(정리 문서 7절 "사용할 모델")
 * @param monthlyLimitUsd 사용자마다 한 달(서울 기준 달력 월) 사용액 상한, 달러(정리 문서 7절 "무료 한도")
 * @param prices          모델 → 백만 토큰당 단가(달러). 서버 쪽 fallback 이 넘겨줄 수 있는 모델도 넣는다
 */
@ConfigurationProperties(prefix = "app.llm")
record LlmProperties(String apiKey, String model, BigDecimal monthlyLimitUsd, Map<String, LlmPrice> prices) {

	/**
	 * 백만 토큰당 단가(달러).
	 *
	 * @param cacheWrite5m 5분 캐시에 쓰는 입력
	 * @param cacheWrite1h 1시간 캐시에 쓰는 입력
	 * @param cacheRead    캐시에서 읽은 입력
	 */
	record LlmPrice(BigDecimal input, BigDecimal output, BigDecimal cacheWrite5m, BigDecimal cacheWrite1h,
			BigDecimal cacheRead) {
	}

}
