package com.readme.auth;

/**
 * 로그인과 메일 보내기에 쓰는 계정의 값.
 *
 * @param email        가입할 때 넣은 그대로의 이메일. 메일은 이 주소로 보낸다
 * @param passwordHash 구글로만 가입한 계정은 null
 * @param verified     이메일 인증을 마쳤는지
 */
record Credentials(long id, String email, String passwordHash, boolean verified) {
}
