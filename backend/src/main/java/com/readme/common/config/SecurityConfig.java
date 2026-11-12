package com.readme.common.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 로그인 확인은 서버 세션 쿠키로 한다(Spring Session JDBC).
 *
 * <p>지금 들어 있는 것: CORS, CSRF, 로그인이 필요한 주소 구분, 로그인 전 요청에 대한 401 응답.
 * <p>로그인 기능을 만들 때 더할 것: 이메일·비밀번호 로그인(/api/auth/login), 구글 로그인(oauth2Login), 로그아웃.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final AppProperties app;

	public SecurityConfig(AppProperties app) {
		this.app = app;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.cors(Customizer.withDefaults())
			// spa(): CSRF 토큰을 XSRF-TOKEN 쿠키로 내려 주고, 프런트가 X-XSRF-TOKEN 헤더로 돌려보낸 값을 확인한다.
			.csrf((csrf) -> csrf.spa().csrfTokenRepository(csrfTokenRepository()))
			.authorizeHttpRequests((auth) -> auth
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers("/error").permitAll()
				.requestMatchers("/api/auth/**").permitAll()
				.anyRequest().authenticated())
			// 로그인 전 요청은 로그인 화면으로 보내지 않고 401 과 { code, message } 를 돌려준다.
			.exceptionHandling((exceptions) -> exceptions.authenticationEntryPoint((request, response, exception) -> {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				response.setCharacterEncoding(StandardCharsets.UTF_8.name());
				response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"로그인이 필요합니다\"}");
			}));
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

	private CookieCsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		if (StringUtils.hasText(this.app.csrfCookieDomain())) {
			// 프런트(app.도메인)의 스크립트가 서버(api.도메인)가 내려 준 쿠키를 읽을 수 있게 상위 도메인에 둔다.
			repository.setCookieCustomizer((cookie) -> cookie.domain(this.app.csrfCookieDomain()));
		}
		return repository;
	}

}
