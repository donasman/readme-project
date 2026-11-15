package com.readme.auth;

import java.time.LocalDate;

/**
 * 가입이 인증 전 계정을 만들거나 새로 차지했을 때 낸다(로그인 설계 4.1, 2026-11-16). 이메일 가입이 받은 생년월일·성별이고,
 * 구글이 인증 전 계정을 차지했을 때는 둘 다 null 이다. 받는 쪽(profile 의 Profiles)이 같은 트랜잭션에서 프로필에 넣는다.
 *
 * <p>인증 전 계정은 로그인한 적이 없어 앞서 같은 이메일로 가입한 사람의 값 말고는 프로필에 들어간 것이 없다. 그 사람은 남일 수 있으므로
 * 받는 쪽은 빈 칸만 채우지 않고 이 값으로 바꾼다. auth 는 profile 을 불러오지 않는다(AccountDeleting 과 같은 이유).
 *
 * @param userId    users.id
 * @param birthDate 생년월일. 없으면 null
 * @param gender    MALE, FEMALE, null
 */
public record SignupValues(long userId, LocalDate birthDate, String gender) {
}
