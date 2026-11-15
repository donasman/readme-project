package com.readme.auth;

import java.io.IOException;
import java.util.Locale;
import java.util.OptionalLong;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
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
 *
 * <p>로그인한 채로 구글 연결하기(4.1): 세션에 {@link #LINK_USER} 가 있으면 로그인 대신 그 사람의 계정에 구글 계정을 연결하고
 * ({@link AccountService#linkGoogle}) 프런트의 내 정보로 결과를 붙여 보낸다. 성공이든 실패든 돌아오면 {@link #LINK_USER} 를 지운다.
 *
 * <p>생년월일·성별(4.1, 2026-11-16): 로그인했거나 연결했고 그 사람이 구글 동의 화면에서 생년월일이나 성별을 허락했으면, 구글의
 * access token 으로 People API 를 불러({@link GooglePeople}) 동의를 남기고 프로필의 빈 칸을 채운다
 * ({@link AccountService#receiveGoogleValues}). 실패해도 로그인과 연결은 그대로다. access token 은 Spring 의 구글 필터가
 * 저장소({@link OAuth2AuthorizedClientRepository})에 둔 것을 꺼내 쓰고, 쓰든 안 쓰든 바로 지운다.
 */
@Component
class GoogleLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

	/**
	 * 세션에 둔 구글 연결의 표시. 값은 연결을 시작한 사용자의 id(Long)다. CSRF 를 검사하는 {@code POST /api/me/google-link} 만
	 * 넣는다(MeController). 구글에서 돌아오면 지우고, 다른 로그인이 시작되면 {@link LoginSession#start} 가 지운다.
	 */
	// shortcut: 표시에 시간 제한이 없어 구글에 가지 않고 떠나면 그 세션의 다음 구글 왕복이 로그인 대신 연결이 된다(본인 브라우저의 구글 계정만),
	// 문제가 되면 시작한 시각을 함께 두고 몇 분 지난 표시는 버린다
	static final String LINK_USER = GoogleLoginHandler.class.getName() + ".LINK_USER";

	private static final Logger log = LoggerFactory.getLogger(GoogleLoginHandler.class);

	private final AccountService accounts;

	private final LoginSession loginSession;

	private final AppProperties app;

	private final GooglePeople people;

	/** 구글 로그인이 켜졌을 때만 있다(Spring Boot 가 만든다). */
	private final ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClients;

	GoogleLoginHandler(AccountService accounts, LoginSession loginSession, AppProperties app, GooglePeople people,
			ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClients) {
		this.accounts = accounts;
		this.loginSession = loginSession;
		this.app = app;
		this.people = people;
		this.authorizedClients = authorizedClients;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		OAuth2AccessToken accessToken = takeAccessToken(authentication, request, response);
		Long linkUser = takeLinkUser(request);
		if (linkUser != null) {
			link(linkUser, authentication, accessToken, request, response);
			return;
		}
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
		readGoogleValues(userId.getAsLong(), accessToken);
		response.sendRedirect(this.app.frontendOrigin() + "/");
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		log.info("구글 로그인 실패: {}", (exception instanceof OAuth2AuthenticationException oauth2)
				? oauth2.getError().getErrorCode() : exception.getClass().getSimpleName());
		// 실패하면 보안 필터가 세션의 로그인 정보를 바꾸지 않는다. 연결을 시작한 사람은 로그인한 채로 내 정보로 돌아간다
		response.sendRedirect((takeLinkUser(request) != null) ? accountUrl("error") : errorUrl());
	}

	/**
	 * 구글 연결의 끝. 결과와 상관없이 세션의 로그인은 연결을 시작한 사람이다. 연결했을 때만 구글의 생년월일·성별을 읽는다. 그 구글 계정이
	 * 다른 사람의 것이면(in-use) 이 사람의 프로필에 넣지 않는다.
	 */
	private void link(long userId, Authentication authentication, OAuth2AccessToken accessToken,
			HttpServletRequest request, HttpServletResponse response) throws IOException {
		AccountService.GoogleLink result = null;
		try {
			OidcUser google = (OidcUser) authentication.getPrincipal();
			result = this.accounts.linkGoogle(userId, google.getSubject(), google.getEmail());
		}
		catch (RuntimeException ex) {
			log.warn("구글 연결을 마치지 못했다: {}", ex.getClass().getSimpleName());
		}
		// 보안 필터가 세션의 로그인 정보를 구글의 것으로 바꿔 두었다. 연결을 시작한 사람으로 되돌린다
		this.loginSession.start(userId, request, response);
		if (result == AccountService.GoogleLink.LINKED) {
			readGoogleValues(userId, accessToken);
		}
		response.sendRedirect(accountUrl((result != null) ? result.name().toLowerCase(Locale.ROOT).replace('_', '-') : "error"));
	}

	/**
	 * Spring 의 구글 필터가 저장소에 둔 이번 로그인의 access token 을 꺼내고 저장소에서 지운다. 저장소의 기본은 서버 메모리라서
	 * 지우지 않으면 구글 계정마다 하나씩 쌓인다. 구글 로그인이 꺼져 있거나 없으면 null.
	 */
	private OAuth2AccessToken takeAccessToken(Authentication authentication, HttpServletRequest request,
			HttpServletResponse response) {
		OAuth2AuthorizedClientRepository repository = this.authorizedClients.getIfAvailable();
		if (repository == null || !(authentication instanceof OAuth2AuthenticationToken google)) {
			return null;
		}
		String registrationId = google.getAuthorizedClientRegistrationId();
		OAuth2AuthorizedClient client = repository.loadAuthorizedClient(registrationId, authentication, request);
		repository.removeAuthorizedClient(registrationId, authentication, request, response);
		return (client != null) ? client.getAccessToken() : null;
	}

	/**
	 * 구글 동의 화면에서 생년월일이나 성별을 허락했으면 People API 로 읽어 동의를 남기고 프로필의 빈 칸에 넣는다. 허락하지 않았으면
	 * 묻지 않는다. 무엇이 잘못되어도 로그인을 막지 않는다.
	 */
	private void readGoogleValues(long userId, OAuth2AccessToken accessToken) {
		if (accessToken == null) {
			return;
		}
		try {
			this.people.read(accessToken)
				.ifPresent((values) -> this.accounts.receiveGoogleValues(userId, values.birthDate(), values.gender()));
		}
		catch (RuntimeException ex) {
			log.warn("구글의 생년월일·성별을 넣지 못했다: 사용자 {}, {}", userId, ex.getClass().getSimpleName());
		}
	}

	/** 세션의 구글 연결 표시를 꺼내고 지운다. 없으면 null. */
	private static Long takeLinkUser(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			return null;
		}
		Long userId = (Long) session.getAttribute(LINK_USER);
		session.removeAttribute(LINK_USER);
		return userId;
	}

	private String errorUrl() {
		return this.app.frontendOrigin() + "/login?error=google";
	}

	/** 프런트의 내 정보. result 는 linked, in-use, has-google, error 다(로그인 설계 7절). */
	private String accountUrl(String result) {
		return this.app.frontendOrigin() + "/settings/account?google=" + result;
	}

}
