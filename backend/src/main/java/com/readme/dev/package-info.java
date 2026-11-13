/**
 * 내 PC 에서만 쓰는 것. dev 프로필이고 prod 가 아닐 때만 등록된다.
 *
 * <ul>
 * <li>{@link com.readme.dev.DevLoginController}: 임시 로그인. 실제 로그인이 생기면 지운다.</li>
 * <li>{@link com.readme.dev.DevSeedData}: 개발용 사용자와 예시 그룹·공고(resources/dev/seed.sql). 운영 마이그레이션에는 넣지 않는다.</li>
 * </ul>
 */
package com.readme.dev;
