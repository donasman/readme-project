package com.readme.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.* 값.
 *
 * @param frontendOrigin   프런트 주소. 이 주소에서 오는 요청만 쿠키와 함께 받는다(CORS). 메일과 초대의 링크도 이 주소로 만든다.
 * @param csrfCookieDomain CSRF 쿠키를 내려 줄 도메인. 프런트와 서버가 app.·api. 로 나뉘는 배포 환경에서
 *                         상위 도메인을 넣는다. 비어 있으면 서버 주소에만 내려 준다(개발).
 * @param mailFrom         가입 확인·비밀번호 재설정 메일의 보내는 사람. 예: {@code 리드미 <no-reply@도메인>}
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String frontendOrigin, String csrfCookieDomain, String mailFrom) {
}
