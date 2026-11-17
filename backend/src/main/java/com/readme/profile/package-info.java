/**
 * 기본 프로필: 인적사항(이름, 생년월일, 성별)과 매칭용 칸(학력, 자격증, 어학, 경력). 사람마다 한 벌이고 나만 본다.
 *
 * <p>테이블: profiles, profile_educations, profile_certificates, profile_languages, profile_careers.
 * API: GET·PUT /api/me/profile. 연차는 경력 칸으로 센다(CareerMonths).
 * <p>뒤에 붙일 것: 가입 값(생년월일·성별)을 넣기(남은 작업 4단계), 자료에서 뽑은 프로필 값으로 빈 칸만 채우기(5단계).
 * <p>근거: docs/리드미_설계_프로필.md.
 */
package com.readme.profile;
