package com.readme.auth;

import java.time.LocalDate;
import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;

/**
 * 로그인·가입·재설정과 내 계정(/api/me) API 가 받는 본문. 길이 제한은 users 칸(이메일 320자, 표시 이름 50자)과 같다.
 * 이메일과 표시 이름은 검사하기 전에 앞뒤 공백을 지운다(레코드의 생성자). 비밀번호는 그대로 쓴다.
 */
final class AuthRequests {

	/** 가입에서 받는 생년월일의 가장 이른 날. 프로필 고치기와 같다. */
	private static final LocalDate OLDEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

	private AuthRequests() {
	}

	/**
	 * POST /api/auth/signup. 생년월일("YYYY-MM-DD")과 성별("male", "female")은 선택이고, 넣으려면 생년월일·성별의 수집·이용 동의
	 * (birthGenderConsent)가 있어야 한다. 동의 둘은 빠지면 false 다(로그인 설계 4.1, 2026-11-16).
	 */
	record SignupRequest(@NotBlank @Size(max = 50) String name, @NotBlank @Email @Size(max = 320) String email,
			@PasswordRule String password, LocalDate birthDate, String gender, Boolean birthGenderConsent,
			Boolean eventNoticeConsent) {

		SignupRequest {
			name = strip(name);
			email = strip(email);
		}

		/**
		 * 가입 값과 동의. 400 INVALID_INPUT: 생년월일이 오늘(서울) 뒤나 1900년 앞, 성별이 male·female 이 아님(빈 글자는 넣지 않은 것),
		 * 동의 없이 넣은 생년월일·성별. 메시지는 프로필 고치기와 같다.
		 */
		SignupExtras extras(LocalDate today) {
			String sex = (this.gender == null || this.gender.isBlank()) ? null : this.gender.strip().toUpperCase(Locale.ROOT);
			if (sex != null && !sex.equals("MALE") && !sex.equals("FEMALE")) {
				throw invalid("성별은 male, female 중 하나이거나 비워 주세요");
			}
			if (this.birthDate != null && (this.birthDate.isAfter(today) || this.birthDate.isBefore(OLDEST_BIRTH_DATE))) {
				throw invalid("생년월일을 확인해 주세요");
			}
			boolean consent = Boolean.TRUE.equals(this.birthGenderConsent);
			if ((this.birthDate != null || sex != null) && !consent) {
				throw invalid("생년월일이나 성별을 넣으려면 수집·이용에 동의해 주세요");
			}
			return new SignupExtras(this.birthDate, sex, consent, Boolean.TRUE.equals(this.eventNoticeConsent));
		}

	}

	/**
	 * 가입에서 받은 선택 값과 동의.
	 *
	 * @param gender             MALE, FEMALE, null
	 * @param birthGenderConsent 생년월일·성별을 통계와 생일 이벤트에 쓰는 데 동의했다. 생년월일이나 성별이 있으면 늘 true 다
	 * @param eventNoticeConsent 이벤트·혜택 안내를 받는 데 동의했다
	 */
	record SignupExtras(LocalDate birthDate, String gender, boolean birthGenderConsent, boolean eventNoticeConsent) {
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

	private static ApiException invalid(String message) {
		return new ApiException(CommonErrorCode.INVALID_INPUT, message);
	}

}
