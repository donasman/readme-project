set search_path to readme;

-- 2026-11-17 사용자 결정: 성별은 받지 않는다(프로필, 가입, 구글 모두). 가입 화면의 동의 둘(생년월일·성별의 통계·이벤트 이용,
-- 이벤트·혜택 안내)도 받지 않는다. 생년월일은 기본 프로필의 선택 칸으로만 남는다(이력서 칸, 나만 본다).
-- 운영 전이라 지워지는 실제 데이터는 없다.
alter table profiles drop column gender;

alter table users
  drop column birth_gender_consent_at,
  drop column event_notice_consent_at;
