package com.readme.auth;

import java.time.LocalDate;

/**
 * 구글 로그인이나 구글 연결 뒤에 People API 로 읽은 생년월일·성별(로그인 설계 4.1, 2026-11-16). 받는 쪽(profile 의 Profiles)이
 * 같은 트랜잭션에서 프로필의 빈 칸만 채운다. 이미 있는 값(가입 값, 사용자가 고친 값)은 덮어쓰지 않는다.
 *
 * @param userId    users.id
 * @param birthDate 연도까지 있는 생년월일. 없으면 null
 * @param gender    MALE, FEMALE, null
 */
public record GoogleValues(long userId, LocalDate birthDate, String gender) {
}
