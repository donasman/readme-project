package com.readme.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 컨트롤러 메서드의 {@link LoginUser} 인자에 붙여 지금 로그인한 사람을 받는다.
 *
 * <pre>GroupsResponse groups(@CurrentUser LoginUser user)</pre>
 *
 * <p>세션의 로그인 정보가 LoginUser 가 아니면 조용히 null 을 주지 않고 오류를 낸다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(errorOnInvalidType = true)
public @interface CurrentUser {
}
