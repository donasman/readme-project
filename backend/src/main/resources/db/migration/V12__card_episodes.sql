set search_path to readme;

-- 장면 단위 카드와 어려움·배운 점(경험카드 설계 4.8, 2026-11-17 사용자 결정).
-- 카드는 장면 하나이고, 같은 경험 이름(앞뒤 공백·대소문자 무시)의 카드가 한 묶음이다. 지금 있는 카드는 경험 이름을 비워 둔다(C9).
alter table experience_cards
  add column experience varchar(200),   -- 경험 이름. 없으면 혼자인 카드
  add column obstacle   text,           -- 어려움(SOAR 의 O)
  add column learning   text;           -- 배운 점(STAR-L·CARL 의 L)
create index experience_cards_experience_idx on experience_cards (user_id, lower(btrim(experience)));
