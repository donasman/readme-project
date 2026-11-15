package com.readme.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml 의 app.google.* 값. 운영과 개발은 넣지 않고 기본값을 쓴다.
 *
 * @param peopleUrl 구글 People API 의 주소. 테스트만 내 PC 에 띄운 가짜로 바꾼다(GoogleLoginTests)
 */
@ConfigurationProperties(prefix = "app.google")
record GoogleProperties(@DefaultValue("https://people.googleapis.com") String peopleUrl) {
}
