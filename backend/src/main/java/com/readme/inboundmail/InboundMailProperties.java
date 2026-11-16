package com.readme.inboundmail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.inbound-mail.* 값(메일 설계 M1, M4).
 *
 * @param domain 수신 전용 도메인. 수신 주소는 {@code <토큰>@<domain>}. 비어 있으면 수신 주소가 없고 어떤 메일도 받지 않는다
 * @param secret Worker 와 함께 쓰는 서명 비밀 값. 비어 있으면 받는 API 가 모두 401 이다
 */
@ConfigurationProperties(prefix = "app.inbound-mail")
record InboundMailProperties(String domain, String secret) {

	boolean hasDomain() {
		return this.domain != null && !this.domain.isBlank();
	}

}
