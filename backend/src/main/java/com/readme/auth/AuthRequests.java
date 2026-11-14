package com.readme.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인·가입·재설정 API 가 받는 본문. 길이 제한은 users 칸(이메일 320자, 표시 이름 50자)과 같다.
 * 이메일과 표시 이름은 검사하기 전에 앞뒤 공백을 지운다(레코드의 생성자). 비밀번호는 그대로 쓴다.
 */
final class AuthRequests {

	private AuthRequests() {
	}

	/** POST /api/auth/signup */
	record SignupRequest(@NotBlank @Size(max = 50) String name, @NotBlank @Email @Size(max = 320) String email,
			@PasswordRule String password) {

		SignupRequest {
			name = strip(name);
			email = strip(email);
		}

	}

	/** POST /api/auth/login. 비밀번호는 규칙을 보지 않는다(틀린 비밀번호와 같게 401 이다). */
	record LoginRequest(@NotBlank String email, @NotBlank String password) {

		LoginRequest {
			email = strip(email);
		}

	}

	/** POST /api/auth/verify-email/resend, POST /api/auth/password-reset/request */
	record EmailRequest(@NotBlank String email) {

		EmailRequest {
			email = strip(email);
		}

	}

	/** POST /api/auth/verify-email, POST /api/auth/password-reset/check. 메일 링크의 토큰이다. */
	record TokenRequest(@NotBlank String token) {
	}

	/** POST /api/auth/password-reset/confirm */
	record ResetPasswordRequest(@NotBlank String token, @PasswordRule String password) {
	}

	private static String strip(String text) {
		return (text != null) ? text.strip() : null;
	}

}
