package com.readme.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.readme.common.error.ApiError;
import com.readme.common.error.CommonErrorCode;
import com.readme.common.error.ErrorCode;

/**
 * 로그인 확인은 서버 세션 쿠키로 한다(Spring Session JDBC).
 *
 * <p>지금 들어 있는 것: CORS, CSRF, 로그인이 필요한 주소 구분, 로그아웃, 비밀번호 해시, 보안 필터가 막은 요청의 오류 응답(401, 403),
 * 구글 로그인(oauth2Login, 구글 클라이언트 값이 있을 때만), 받은 메일을 넘기는 주소(로그인과 CSRF 없이, 서명으로 확인한다).
 * 이메일·비밀번호 로그인은 auth 의 AuthController 가, 구글 로그인의 끝은 auth 의 GoogleLoginHandler 가,
 * 로그인 세션을 만드는 것은 auth 의 LoginSession 이 한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	/** Cloudflare Email Worker 가 받은 메일을 넘기는 주소(메일 설계 4.1) */
	private static final String INBOUND_MAIL = "/api/inbound-mail";

	private final AppProperties app;

	private final JsonMapper jsonMapper;

	public SecurityConfig(AppProperties app, JsonMapper jsonMapper) {
		this.app = app;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * @param googleClient       구글 클라이언트(spring.security.oauth2.client.registration.google). 값을 넣었을 때만 있다
	 * @param googleLoginSuccess 구글 로그인의 끝(auth 의 GoogleLoginHandler)
	 * @param googleLoginFailure 같은 것
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
			SecurityContextRepository securityContextRepository, ObjectProvider<ClientRegistrationRepository> googleClient,
			AuthenticationSuccessHandler googleLoginSuccess, AuthenticationFailureHandler googleLoginFailure)
			throws Exception {
		http
			.cors(Customizer.withDefaults())
			// spa(): CSRF 토큰을 XSRF-TOKEN 쿠키로 내려 주고, 프런트가 X-XSRF-TOKEN 헤더로 돌려보낸 값을 확인한다.
			// 쿠키는 읽기 요청의 응답에도 실린다(요청에 없을 때). 그래서 프런트의 첫 로그인 요청도 토큰을 보낼 수 있다(LoginTests).
			// 받은 메일을 넘기는 Worker(메일 설계 M1)에는 세션도 CSRF 토큰도 없다. 서명으로 확인한다(inboundmail 의 InboundMailController)
			.csrf((csrf) -> csrf.spa().csrfTokenRepository(csrfTokenRepository).ignoringRequestMatchers(INBOUND_MAIL))
			// 로그아웃: 세션을 지우고(세션 행과 쿠키) 204 를 준다. 다음 로그인에 쓸 새 XSRF-TOKEN 쿠키도 준다.
			.logout((logout) -> logout.logoutUrl("/api/auth/logout")
				.logoutSuccessHandler((request, response, authentication) -> {
					csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
					response.setStatus(HttpStatus.NO_CONTENT.value());
				}))
			.securityContext((context) -> context.securityContextRepository(securityContextRepository))
			.authorizeHttpRequests((auth) -> auth
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers("/error").permitAll()
				.requestMatchers("/api/auth/**").permitAll()
				// 구글 로그인의 두 주소(로그인 설계 4절). 켜져 있으면 아래 oauth2Login 의 필터가 먼저 받는다. 꺼져 있으면 404 다.
				.requestMatchers("/oauth2/authorization/google", "/login/oauth2/code/google").permitAll()
				// 초대 미리 보기. 손님도 그룹 이름을 보고 로그인·가입으로 간다(그룹 설계 4.1). 수락(POST …/accept)은 로그인해야 한다
				.requestMatchers(HttpMethod.GET, "/api/group-invites/*").permitAll()
				// 받은 메일을 넘기는 Worker. 로그인 대신 서명을 본다(메일 설계 M1, 2026-11-16 사용자 결정)
				.requestMatchers(HttpMethod.POST, INBOUND_MAIL).permitAll()
				.anyRequest().authenticated())
			// 로그인 전 요청을 세션에 저장해 두지 않는다. 로그인 뒤 돌아갈 화면은 프런트가 정하고,
			// 저장해 두면 로그인 전 요청마다 세션 행이 생긴다.
			.requestCache((cache) -> cache.requestCache(new NullRequestCache()))
			// 보안 필터가 막은 요청도 컨트롤러의 오류와 같은 { code, message } 로 답한다.
			// 로그인 전이면 로그인 화면으로 보내지 않고 401 을 준다.
			.exceptionHandling((exceptions) -> exceptions
				.authenticationEntryPoint((request, response, exception) ->
					writeError(response, CommonErrorCode.UNAUTHENTICATED))
				.accessDeniedHandler((request, response, exception) ->
					writeError(response, (exception instanceof CsrfException) ? CommonErrorCode.CSRF_INVALID : CommonErrorCode.FORBIDDEN)));
		// 구글 로그인: 구글 클라이언트 값을 넣었을 때만 켠다(개발은 backend/application-local.yml, 운영은 환경 변수).
		// GET /oauth2/authorization/google 이 구글로 보내고(state 를 세션에 둔다), 구글은 /login/oauth2/code/google 로 돌아온다.
		// 사람을 확인한 뒤의 일과 돌려보낼 곳은 googleLoginSuccess·Failure 가 정한다.
		// 구글의 access token 은 Spring 이 기본 저장소(서버 메모리)에 둔다. googleLoginSuccess 가 꺼내 People API 로 생년월일·성별을
		// 한 번 읽고 바로 지운다(로그인 설계 4.1). 그래서 메모리에 쌓이지 않고 세션에도 남지 않는다.
		if (googleClient.getIfAvailable() != null) {
			http.oauth2Login((google) -> google.successHandler(googleLoginSuccess).failureHandler(googleLoginFailure));
		}
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of(this.app.frontendOrigin()));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	/**
	 * 로그인 정보(SecurityContext)를 두는 곳. 요청 동안은 요청 속성에, 요청 사이에는 세션에 둔다.
	 * 보안 필터와 로그인 처리(auth 의 LoginSession)가 같은 것을 쓴다.
	 */
	@Bean
	SecurityContextRepository securityContextRepository() {
		return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(),
				new HttpSessionSecurityContextRepository());
	}

	/** CSRF 토큰을 XSRF-TOKEN 쿠키에 둔다. 보안 필터와 로그인 처리(로그인할 때 토큰을 새로 만든다)가 같은 것을 쓴다. */
	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		if (StringUtils.hasText(this.app.csrfCookieDomain())) {
			// 프런트(app.도메인)의 스크립트가 서버(api.도메인)가 내려 준 쿠키를 읽을 수 있게 상위 도메인에 둔다.
			repository.setCookieCustomizer((cookie) -> cookie.domain(this.app.csrfCookieDomain()));
		}
		return repository;
	}

	/** 비밀번호 해시. 지금은 BCrypt 이고, 해시 앞에 {bcrypt} 가 붙어 나중에 방식을 바꿀 수 있다. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	private void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		this.jsonMapper.writeValue(response.getWriter(), ApiError.of(code));
	}

}
