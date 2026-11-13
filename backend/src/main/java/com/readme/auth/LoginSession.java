package com.readme.auth;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * 로그인 세션을 만든다. 로그인 방식마다 사람을 확인하는 방법만 다르고, 확인한 뒤에는 모두 {@link #start} 를 부른다.
 *
 * <p>하는 일: 세션 id 를 새로 바꾸고(세션 고정 공격 막기), CSRF 토큰을 새로 만들고, 로그인 정보를 세션에 저장한다.
 * 세션은 Spring Session JDBC 가 DB(spring_session)에 둔다.
 */
@Component
public class LoginSession {

	private final SecurityContextRepository contextRepository;

	private final SessionAuthenticationStrategy sessionStrategy;

	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	public LoginSession(SecurityContextRepository contextRepository, CsrfTokenRepository csrfTokenRepository) {
		this.contextRepository = contextRepository;
		this.sessionStrategy = new CompositeSessionAuthenticationStrategy(List.of(
			new ChangeSessionIdAuthenticationStrategy(),
			new CsrfAuthenticationStrategy(csrfTokenRepository)));
	}

	/** 세션에 넣는 로그인 정보. 테스트도 이것으로 로그인한 요청을 만든다. */
	public static Authentication authenticationOf(long userId) {
		return UsernamePasswordAuthenticationToken.authenticated(new LoginUser(userId), null, List.of());
	}

	/** 이 요청의 사람을 userId 로 로그인시킨다. 이미 다른 사람으로 로그인해 있었으면 바뀐다. */
	public void start(long userId, HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = authenticationOf(userId);
		this.sessionStrategy.onAuthentication(authentication, request, response);

		SecurityContext context = this.contextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		this.contextHolder.setContext(context);
		this.contextRepository.saveContext(context, request, response);

		// 위에서 CSRF 토큰을 지우고 새 토큰을 미뤄 두었다. 지금 꺼내야 새 XSRF-TOKEN 쿠키가 이 응답에 실린다.
		// 꺼내지 않으면 응답에는 지운 쿠키만 실려, 프런트가 로그인 직후 보내는 요청이 CSRF 검사에 걸린다.
		CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (csrfToken != null) {
			csrfToken.getToken();
		}
	}

}
