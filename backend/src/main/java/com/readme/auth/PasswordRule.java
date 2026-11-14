package com.readme.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * 새로 정하는 비밀번호의 규칙(2026-11-14 정함): 8자 이상, UTF-8 로 72바이트 이하, 조합 규칙은 없다.
 *
 * <p>72바이트는 비밀번호 해시(BCrypt)가 쓰는 길이다. Spring Security 는 그보다 긴 비밀번호를 해시하지 않고 오류를 낸다.
 * 영문은 72자, 한글은 24자까지다. 앞뒤 공백도 비밀번호의 일부로 본다.
 */
@Documented
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordRule.Validator.class)
@interface PasswordRule {

	int MIN_LENGTH = 8;

	int MAX_BYTES = 72;

	String message() default "비밀번호는 8자 이상, 72바이트 이하입니다";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<PasswordRule, String> {

		@Override
		public boolean isValid(String password, ConstraintValidatorContext context) {
			return password != null && password.codePointCount(0, password.length()) >= MIN_LENGTH
					&& fitsHash(password);
		}

		/** 비밀번호 해시가 받는 길이인지. 이보다 길면 해시를 만들지도, 견주지도 못한다. */
		static boolean fitsHash(String password) {
			return password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
		}

	}

}
