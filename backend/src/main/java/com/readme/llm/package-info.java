/**
 * LLM 호출과 사용량 기록. Claude API 만 쓴다(공식 Java SDK).
 *
 * <p>테이블: llm_usage. 다른 기능이 부르는 것: {@link com.readme.llm.Llm}(requireBudget, ask), {@link com.readme.llm.LlmFeature},
 * {@link com.readme.llm.LlmErrorCode}.
 * <p>흐름: 기능이 사용자의 요청을 받을 때 Llm.requireBudget 으로 이번 달 한도를 보고, 일을 하면서 Llm.ask 로 묻는다.
 * ask 는 ClaudeApi(실제는 AnthropicClaudeApi)를 부르고 LlmUsageRecorder 가 시도마다 토큰과 금액(LlmPrices)을 남긴다.
 * 통합 테스트는 ClaudeApi 를 가짜(FakeClaudeApi)로 바꿔 실제 API 를 부르지 않는다.
 * <p>프롬프트는 resources/prompts/ 에 둔다(카드 추출의 exp-extract.md 는 5단계에서 고친다).
 * <p>근거: docs/리드미_설계_경험카드.md 4.5, 정리 문서 4절(라이브러리)·7절(사용할 모델, 무료 한도).
 */
package com.readme.llm;
