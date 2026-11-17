package com.readme.auth;

import org.springframework.http.HttpStatus;

import com.readme.common.error.ErrorCode;

/**
 * 로그인·가입·재설정의 오류(로그인 설계 4절). 규칙에 맞지 않는 비밀번호는 공통 코드 INVALID_INPUT 이다.
 */
public enum AuthErrorCode implements ErrorCode {

	/** 인증을 마친 계정이 이미 그 이메일을 쓴다. 인증 전 계정이면 새로 넣은 값으로 덮어쓰고 가입을 받는다. */
	EMAIL_TAKEN(HttpStatus.CONFLICT, "이미 가입된 이메일입니다"),

	/** 이메일이나 비밀번호가 틀렸다. 계정이 없을 때와 비밀번호가 틀릴 때를 구분하지 않는다. */
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일이나 비밀번호가 맞지 않습니다"),

	/** 비밀번호는 맞지만 이메일 인증을 마치지 않았다(가정 L4). */
	EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증을 마친 뒤 로그인할 수 있습니다"),

	/**
	 * 그 이메일로 15분 안에 10번 로그인하지 못했다(LoginThrottle). 계정이 없는 이메일도 같다. 비밀번호 바꾸기와 탈퇴 확인에서는
	 * 그 사람이 15분 안에 지금 비밀번호를 10번 틀렸다. 메시지는 셋에 같이 맞게 "로그인"을 넣지 않는다.
	 */
	TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "시도가 너무 많습니다. 잠시 뒤에 다시 시도해 주세요"),

	/** 가입 확인이나 비밀번호 재설정 링크가 지났거나, 이미 썼거나, 없다. 셋을 구분하지 않는다. */
	LINK_EXPIRED(HttpStatus.GONE, "쓸 수 없는 링크입니다. 메일을 다시 받아 주세요"),

	/**
	 * 비밀번호 바꾸기의 지금 비밀번호, 탈퇴 확인의 비밀번호가 틀렸다. 401 이 아니다. 프런트는 401 을 "로그인하지 않았다"로 본다.
	 */
	WRONG_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 맞지 않습니다"),

	/** 비밀번호가 없는 계정(구글로만 가입)의 탈퇴 확인: 넣은 이메일이 계정의 이메일과 다르다(대소문자는 가리지 않는다). */
	WRONG_EMAIL(HttpStatus.BAD_REQUEST, "계정의 이메일과 맞지 않습니다"),

	/** 비밀번호가 없는 계정(구글로만 가입)이 비밀번호를 바꾸려 했다. 재설정 메일로 비밀번호를 만든다. */
	NO_PASSWORD(HttpStatus.CONFLICT, "비밀번호가 없는 계정입니다. 비밀번호 재설정 메일을 받아 비밀번호를 만들어 주세요");

	private final HttpStatus status;

	private final String message;

	AuthErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	@Override
	public HttpStatus status() {
		return this.status;
	}

	@Override
	public String message() {
		return this.message;
	}

}
