package com.readme.postingread;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml 의 app.posting-read.* 값. 운영은 아무것도 넣지 않고 기본값을 쓴다.
 *
 * @param connectTimeout        연결을 기다리는 시간
 * @param readTimeout           연결된 뒤 응답 전체(머리와 본문)를 기다리는 시간
 * @param hostInterval          같은 호스트에 보내는 요청 사이의 최소 간격
 * @param allowLoopbackForTests 테스트가 띄운 내 PC 의 서버(127.0.0.1)를 읽게 한다. 테스트 설정(application-test.yml)만 켠다.
 *                              운영과 개발에서는 넣지 않는다. 켜도 사설망·링크 로컬 주소는 그대로 막힌다
 * @param contact               User-Agent 에 붙이는 연락처. 사이트 운영자가 열어 볼 수 있는 공개 페이지나 mailto: 주소다.
 *                              운영은 FETCHER_CONTACT 로 꼭 넣는다(application-prod.yml). 비어 있으면 프런트 주소를 쓴다(개발)
 */
@ConfigurationProperties(prefix = "app.posting-read")
record PostingReadProperties(@DefaultValue("5s") Duration connectTimeout, @DefaultValue("10s") Duration readTimeout,
		@DefaultValue("1s") Duration hostInterval, @DefaultValue("false") boolean allowLoopbackForTests, String contact) {
}
