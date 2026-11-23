set search_path to readme;

-- 리드미 V15: 입력이 같으면 LLM 을 다시 부르지 않고 저장한 답을 다시 쓴다(2026-11-24 사용자 결정).
-- key 는 보낸 지시문과 사용자 글을 이은 것의 SHA-256(소문자 16진수), answer 는 거르기 전의 LLM 답 그대로다. 누른 적이 없으면 null.
alter table user_postings
  add column card_priority_key     varchar(64),
  add column card_priority_answer  jsonb,
  add column question_match_key    varchar(64),
  add column question_match_answer jsonb;
