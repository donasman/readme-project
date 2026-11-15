package com.readme.auth;

import java.time.Clock;
import java.time.LocalDate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.readme.auth.AuthRequests.EmailRequest;
import com.readme.auth.AuthRequests.LoginRequest;
import com.readme.auth.AuthRequests.ResetPasswordRequest;
import com.readme.auth.AuthRequests.SignupRequest;
import com.readme.auth.AuthRequests.TokenRequest;

/**
 * 이메일·비밀번호 로그인 API(로그인 설계 4절). /api/auth/** 는 로그인 없이 부른다. CSRF 검사는 받는다.
 * 로그아웃(POST /api/auth/logout)은 Spring Security 의 로그아웃 필터가 맡는다(SecurityConfig).
 *
 * <p>돌려줄 값이 없는 요청은 204 다. 메일을 보내는 요청은 계정이 있는지 응답으로 알려 주지 않는다(가입은 409 로 알려 준다).
 */
@RestController
class AuthController {

	private final AccountService service;

	private final AccountQueries queries;

	private final LoginSession loginSession;

	private final Clock clock;

	AuthController(AccountService service, AccountQueries queries, LoginSession loginSession, Clock clock) {
		this.service = service;
		this.queries = queries;
		this.loginSession = loginSession;
		this.clock = clock;
	}

	@PostMapping("/api/auth/signup")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void signup(@Valid @RequestBody SignupRequest request) {
		this.service.signup(request.name(), request.email(), request.password(),
				request.extras(LocalDate.now(this.clock)));
	}

	@PostMapping("/api/auth/verify-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void verifyEmail(@Valid @RequestBody TokenRequest request) {
		this.service.verifyEmail(request.token());
	}

	@PostMapping("/api/auth/verify-email/resend")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resendVerification(@Valid @RequestBody EmailRequest request) {
		this.service.resendVerification(request.email());
	}

	/** 로그인하면 세션 쿠키와 새 XSRF-TOKEN 쿠키를 주고, GET /api/me 와 같은 내 계정을 돌려준다. */
	@PostMapping("/api/auth/login")
	MeResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		long userId = this.service.login(request.email(), request.password());
		this.loginSession.start(userId, httpRequest, httpResponse);
		return this.queries.findMe(userId).orElseThrow();
	}

	@PostMapping("/api/auth/password-reset/request")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void requestPasswordReset(@Valid @RequestBody EmailRequest request) {
		this.service.requestPasswordReset(request.email());
	}

	/** 새 비밀번호 화면을 열 때 링크를 아직 쓸 수 있는지 묻는다(2026-11-14 정함). */
	@PostMapping("/api/auth/password-reset/check")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void checkResetLink(@Valid @RequestBody TokenRequest request) {
		this.service.checkResetLink(request.token());
	}

	@PostMapping("/api/auth/password-reset/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		this.service.resetPassword(request.token(), request.password());
	}

}
