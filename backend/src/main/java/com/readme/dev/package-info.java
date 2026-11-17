/**
 * 내 PC 에서만 쓰는 것. dev 프로필이고 prod 가 아닐 때만 등록된다.
 *
 * <ul>
 * <li>{@link com.readme.dev.DevSeedData}: 개발용 사용자와 예시 그룹·공고(resources/dev/seed.sql). 예시 사용자는 모두 같은
 * 개발용 비밀번호로 실제 로그인(POST /api/auth/login)을 한다. 운영 마이그레이션에는 넣지 않는다.</li>
 * </ul>
 *
 * <p>2026-11-14 까지 있던 임시 로그인(POST /api/auth/dev-login)은 실제 로그인을 만들며 지웠다.
 */
package com.readme.dev;
