package com.readme.auth;

import java.io.IOException;
import java.util.OptionalLong;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;

import com.readme.common.config.AppProperties;

/**
 * 구글 로그인의 끝(로그인 설계 3.2, 4.1). 구글에서 돌아오면 Spring Security 의 oauth2Login 이 부른다(SecurityConfig).
 *
 * <p>구글이 사람을 확인했으면 그 사람의 계정을 찾거나 연결하거나 만들고({@link AccountService#loginWithGoogle}),
 * 이메일 로그인과 같은 {@link LoginSession#start} 로 세션을 만든 뒤 프런트의 홈으로 보낸다. 그래서 세션에는 구글의 토큰이 아니라
 * {@link LoginUser} 가 남고, 세션의 주인 이름은 사용자 id 다.
 *
 * <p>동의를 거부했거나, 구글이 오류를 냈거나, 연결할 계정이 없으면 프런트의 {@code /login?error=google} 로 보낸다.
 * 이유는 나누지 않는다. 기록에는 토큰과 이메일을 남기지 않는다.
 */
@Component
class GoogleLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

	private static final Logger log = LoggerFactory.getLogger(GoogleLoginHandler.class);

	private final AccountService accounts;

	private final LoginSession loginSession;

	private final AppProperties app;

	GoogleLoginHandler(AccountService accounts, LoginSession loginSession, AppProperties app) {
		this.accounts = accounts;
		this.loginSession = loginSession;
		this.app = app;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		OptionalLong userId = OptionalLong.empty();
		try {
			OidcUser google = (OidcUser) authentication.getPrincipal();
			userId = this.accounts.loginWithGoogle(google.getSubject(), google.getEmail(),
					Boolean.TRUE.equals(google.getEmailVerified()), google.getFullName());
		}
		catch (RuntimeException ex) {
			log.warn("구글 로그인을 마치지 못했다: {}", ex.getClass().getSimpleName());
		}
		if (userId.isEmpty()) {
			// 보안 필터가 이미 세션에 넣어 둔 구글 로그인 정보를 지운다. 남기면 LoginUser 가 없는 로그인 상태가 된다.
			new SecurityContextLogoutHandler().logout(request, response, authentication);
			response.sendRedirect(errorUrl());
			return;
		}
		this.loginSession.start(userId.getAsLong(), request, response);
		response.sendRedirect(this.app.frontendOrigin() + "/");
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		log.info("구글 로그인 실패: {}", (exception instanceof OAuth2AuthenticationException oauth2)
				? oauth2.getError().getErrorCode() : exception.getClass().getSimpleName());
		response.sendRedirect(errorUrl());
	}

	private String errorUrl() {
		return this.app.frontendOrigin() + "/login?error=google";
	}

}
