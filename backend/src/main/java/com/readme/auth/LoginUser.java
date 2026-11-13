package com.readme.auth;

import java.io.Serializable;

import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * 로그인한 사람. 세션에 저장되는 로그인 정보(SecurityContext)의 주인공이다.
 *
 * <p>어느 방식으로 로그인하든(이메일·비밀번호, 구글, 개발용 임시 로그인) {@link LoginSession#start} 가 이 값을 넣는다.
 * 컨트롤러는 로그인 방식을 모르고 {@link CurrentUser} 로 이 값을 받는다.
 *
 * @param id users.id
 */
public record LoginUser(long id) implements AuthenticatedPrincipal, Serializable {

	/** 로그와 Spring Security 가 쓰는 이름. 사용자 id 다. */
	@Override
	public String getName() {
		return String.valueOf(this.id);
	}

}
