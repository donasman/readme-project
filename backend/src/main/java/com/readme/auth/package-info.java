/**
 * 계정과 로그인. 이메일·비밀번호 가입, 이메일 인증, 비밀번호 재설정, 구글 로그인.
 *
 * <p>테이블: users, user_identities, email_tokens. API: /api/auth/**, /api/me. 구글 로그인 주소(Spring Security 의 기본 경로):
 * /oauth2/authorization/google, /login/oauth2/code/google.
 *
 * <p>가입 값(생년월일·성별)과 동의(2026-11-16): 동의는 users 에 두고, 생년월일·성별은 이벤트(SignupValues, GoogleValues)로 내
 * profile 이 넣는다. 구글의 값은 People API 로 읽는다(GooglePeople).
 */
package com.readme.auth;
