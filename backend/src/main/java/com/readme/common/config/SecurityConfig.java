package com.readme.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
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
 * <p>지금 들어 있는 것: CORS, CSRF, 로그인이 필요한 주소 구분, 보안 필터가 막은 요청의 오류 응답(401, 403).
 * 로그인 세션을 만드는 것은 auth 의 LoginSession 이다.
 * <p>로그인 기능을 만들 때 더할 것: 이메일·비밀번호 로그인(/api/auth/login), 구글 로그인(oauth2Login), 로그아웃.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final AppProperties app;

	private final JsonMapper jsonMapper;

	public SecurityConfig(AppProperties app, JsonMapper jsonMapper) {
		this.app = app;
		this.jsonMapper = jsonMapper;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
			SecurityContextRepository securityContextRepository) throws Exception {
		http
			.cors(Customizer.withDefaults())
			// spa(): CSRF 토큰을 XSRF-TOKEN 쿠키로 내려 주고, 프런트가 X-XSRF-TOKEN 헤더로 돌려보낸 값을 확인한다.
			.csrf((csrf) -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
			.securityContext((context) -> context.securityContextRepository(securityContextRepository))
			.authorizeHttpRequests((auth) -> auth
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers("/error").permitAll()
				.requestMatchers("/api/auth/**").permitAll()
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

	private void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		this.jsonMapper.writeValue(response.getWriter(), ApiError.of(code));
	}

}
