set search_path to readme;

-- 가입에서 받는 선택 동의 둘(docs/리드미_설계_로그인.md 4.1, 2026-11-16). 동의한 때를 두고, null 이면 동의하지 않은 것이다.
alter table users
  add column birth_gender_consent_at timestamptz,   -- 생년월일·성별을 통계와 생일 이벤트에 쓰는 것
  add column event_notice_consent_at timestamptz;   -- 이벤트·혜택 안내를 받는 것
