package com.readme.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;

@RestController
class MeController {

	private final AccountQueries accounts;

	MeController(AccountQueries accounts) {
		this.accounts = accounts;
	}

	/** 내 계정. 로그인하지 않았으면 보안 필터가 401 을 준다. 세션은 남았는데 계정이 지워졌으면 여기서 401 을 준다. */
	@GetMapping("/api/me")
	MeResponse me(@CurrentUser LoginUser user) {
		return this.accounts.findMe(user.id()).orElseThrow(() -> new ApiException(CommonErrorCode.UNAUTHENTICATED));
	}

}
