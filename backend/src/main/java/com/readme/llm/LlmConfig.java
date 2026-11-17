package com.readme.llm;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
class LlmConfig {

	private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

	/** 키가 없어도 서버는 뜬다. 그때는 LLM 을 쓰는 기능만 503 LLM_UNAVAILABLE 이다(구글 로그인처럼 값이 있을 때만 켠다). */
	@Bean
	AnthropicClaudeApi claudeApi(LlmProperties properties) {
		if (!StringUtils.hasText(properties.apiKey())) {
			log.info("Claude API 키(app.llm.api-key)가 없어 LLM 을 쓰는 기능은 503 LLM_UNAVAILABLE 로 답한다");
			return new AnthropicClaudeApi(null);
		}
		return new AnthropicClaudeApi(AnthropicOkHttpClient.builder().apiKey(properties.apiKey()).build());
	}

	@Bean
	LlmPrices llmPrices(LlmProperties properties) {
		return new LlmPrices(properties.prices());
	}

}
