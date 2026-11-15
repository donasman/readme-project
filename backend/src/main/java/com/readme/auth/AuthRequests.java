package com.readme.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인·가입·재설정과 내 계정(/api/me) API 가 받는 본문. 길이 제한은 users 칸(이메일 320자, 표시 이름 50자)과 같다.
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

	/**
	 * POST /api/auth/login. 비밀번호는 규칙을 보지 않는다(틀린 비밀번호와 같게 401 이다).
	 * 이메일은 길이만 본다. LoginThrottle 이 이메일 글자를 메모리에 들고 있어서다.
	 */
	record LoginRequest(@NotBlank @Size(max = 320) String email, @NotBlank String password) {

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

	/** PUT /api/me. 표시 이름 바꾸기 */
	record RenameRequest(@NotBlank @Size(max = 50) String name) {

		RenameRequest {
			name = strip(name);
		}

	}

	/**
	 * PUT /api/me/password. 지금 비밀번호는 규칙도 빈칸도 보지 않는다. 틀리면 400 WRONG_PASSWORD 이고, 비밀번호가 없는 계정은
	 * 무엇을 넣어도 409 NO_PASSWORD 다.
	 */
	record ChangePasswordRequest(String currentPassword, @PasswordRule String newPassword) {
	}

	/** POST /api/me/withdraw. 비밀번호가 있는 계정은 password, 없는 계정(구글로만 가입)은 email 로 확인한다. */
	record WithdrawRequest(String password, String email) {

		WithdrawRequest {
			email = strip(email);
		}

	}

	private static String strip(String text) {
		return (text != null) ? text.strip() : null;
	}

}
