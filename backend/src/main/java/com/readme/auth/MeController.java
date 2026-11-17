package com.readme.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.readme.auth.AuthRequests.ChangePasswordRequest;
import com.readme.auth.AuthRequests.RenameRequest;
import com.readme.auth.AuthRequests.WithdrawRequest;
import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;

/**
 * 내 계정: 읽기, 표시 이름, 비밀번호 바꾸기, 구글 연결 시작, 탈퇴(로그인 설계 4절).
 */
@RestController
class MeController {

	private final AccountQueries accounts;

	private final MeService service;

	private final WithdrawalQueries withdrawals;

	MeController(AccountQueries accounts, MeService service, WithdrawalQueries withdrawals) {
		this.accounts = accounts;
		this.service = service;
		this.withdrawals = withdrawals;
	}

	/** 내 계정. 로그인하지 않았으면 보안 필터가 401 을 준다. 세션은 남았는데 계정이 지워졌으면 여기서 401 을 준다. */
	@GetMapping("/api/me")
	MeResponse me(@CurrentUser LoginUser user) {
		return this.accounts.findMe(user.id()).orElseThrow(() -> new ApiException(CommonErrorCode.UNAUTHENTICATED));
	}

	/** 표시 이름 바꾸기. 바뀐 내 계정(GET /api/me 와 같은 모양)을 돌려준다. */
	@PutMapping("/api/me")
	MeResponse rename(@Valid @RequestBody RenameRequest request, @CurrentUser LoginUser user) {
		this.service.rename(user.id(), request.name());
		return me(user);
	}

	/** 로그인한 채로 비밀번호 바꾸기. 이 브라우저의 로그인은 두고 다른 기기의 로그인은 모두 끊는다. */
	@PutMapping("/api/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changePassword(@Valid @RequestBody ChangePasswordRequest request, @CurrentUser LoginUser user,
			HttpServletRequest httpRequest) {
		HttpSession session = httpRequest.getSession(false);
		this.service.changePassword(user.id(), request.currentPassword(), request.newPassword(),
				(session != null) ? session.getId() : null);
	}

	/**
	 * 로그인한 채로 구글 연결을 시작한다(로그인 설계 4.1). 이 세션에 연결할 사람을 적어 둘 뿐이다. 프런트는 204 를 받으면 페이지를
	 * {@code GET /oauth2/authorization/google} 로 옮기고, 구글에서 돌아오면 GoogleLoginHandler 가 연결한다.
	 * 로그인 주소로 바로 가지 않고 이것을 거치는 것은 CSRF 검사를 받게 하려는 것이다. 남의 사이트가 연결을 시작하지 못한다.
	 */
	@PostMapping("/api/me/google-link")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void startGoogleLink(@CurrentUser LoginUser user, HttpServletRequest httpRequest) {
		httpRequest.getSession().setAttribute(GoogleLoginHandler.LINK_USER, user.id());
	}

	/** 탈퇴하면 지워질 것의 수와 내가 그룹장인 그룹(누가 이어받는지, 혼자면 지워진다). */
	@GetMapping("/api/me/withdrawal")
	WithdrawalResponse withdrawal(@CurrentUser LoginUser user) {
		return this.withdrawals.find(user.id());
	}

	/** 탈퇴. 계정과 이 사람의 세션을 모두 지우고, 이 브라우저의 세션 쿠키도 지운다. 응답 뒤에는 로그인하지 않은 상태다. */
	@PostMapping("/api/me/withdraw")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void withdraw(@RequestBody WithdrawRequest request, @CurrentUser LoginUser user, HttpServletRequest httpRequest) {
		this.service.withdraw(user.id(), request.password(), request.email());
		// 세션 행은 서비스가 커밋 뒤에 지웠다. 여기서 끝내야 이 요청이 끝날 때 세션을 다시 저장하지 않고 쿠키를 지운다
		HttpSession session = httpRequest.getSession(false);
		if (session != null) {
			session.invalidate();
		}
	}

}
