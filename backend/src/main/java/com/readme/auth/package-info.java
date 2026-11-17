/**
 * 계정과 로그인. 이메일·비밀번호 가입, 이메일 인증, 비밀번호 재설정, 구글 로그인.
 *
 * <p>테이블: users, user_identities, email_tokens. API: /api/auth/**, /api/me. 구글 로그인 주소(Spring Security 의 기본 경로):
 * /oauth2/authorization/google, /login/oauth2/code/google.
 */
package com.readme.auth;
