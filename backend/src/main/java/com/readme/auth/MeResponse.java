package com.readme.auth;

import java.util.List;

/**
 * GET /api/me 의 응답. 내 계정.
 *
 * @param name        표시 이름(users.display_name)
 * @param hasPassword 비밀번호로도 로그인할 수 있는지. 구글로만 가입하면 false
 * @param providers   연결된 소셜 로그인. 지금은 "google" 뿐이다
 * @param google      연결된 구글 계정. 연결하지 않았으면 null. 설정 화면의 AccountInfo.google 과 같은 모양이다
 */
public record MeResponse(long id, String name, String email, boolean hasPassword, List<String> providers,
		Google google) {

	/** @param email 연결할 때 구글이 준 이메일(user_identities.provider_email). 계정의 이메일과 다를 수 있다 */
	public record Google(String email) {
	}

}
