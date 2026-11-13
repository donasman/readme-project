package com.readme.dev;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.readme.auth.AccountQueries;
import com.readme.auth.LoginSession;
import com.readme.auth.MeResponse;
import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;

/**
 * 임시 로그인. 비밀번호 없이 이메일만으로 그 사람이 된다. 실제 로그인이 생기면 이 클래스를 지운다(backend/CLAUDE.md).
 *
 * <p>요청마다 고정 사용자를 끼워 넣지 않는다. 실제 로그인과 같은 {@link LoginSession} 으로 진짜 세션을 만들고,
 * 그룹 API 는 그 세션에서 지금 로그인한 사람을 읽는다. 다른 이메일로 다시 부르면 사람이 바뀐다.
 *
 * <p>주소가 /api/auth/** 아래라 로그인 없이 부를 수 있다(SecurityConfig 의 기존 규칙). CSRF 검사는 받는다.
 */
@Profile("dev & !prod")
@RestController
class DevLoginController {

	private static final Logger log = LoggerFactory.getLogger(DevLoginController.class);

	private final AccountQueries accounts;

	private final LoginSession loginSession;

	DevLoginController(AccountQueries accounts, LoginSession loginSession) {
		this.accounts = accounts;
		this.loginSession = loginSession;
		log.warn("개발용 임시 로그인이 켜져 있다: POST /api/auth/dev-login. 운영에서 이 줄이 보이면 안 된다");
	}

	@PostMapping("/api/auth/dev-login")
	MeResponse login(@Valid @RequestBody DevLoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		long userId = this.accounts.findIdByEmail(request.email())
			.orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "그 이메일의 사용자가 없습니다"));
		this.loginSession.start(userId, httpRequest, httpResponse);
		return this.accounts.findMe(userId).orElseThrow();
	}

	record DevLoginRequest(@NotBlank String email) {
	}

}
