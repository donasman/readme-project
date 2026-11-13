package com.readme.auth;

import java.util.List;

/**
 * GET /api/me 의 응답. 내 계정.
 *
 * @param name        표시 이름(users.display_name)
 * @param hasPassword 비밀번호로도 로그인할 수 있는지. 구글로만 가입하면 false
 * @param providers   연결된 소셜 로그인. 지금은 "google" 뿐이다
 */
public record MeResponse(long id, String name, String email, boolean hasPassword, List<String> providers) {
}
