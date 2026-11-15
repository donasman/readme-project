-- 리드미 V9: 공고 분석(LLM). 공고 본체에 두어 같은 공고를 올린 그룹과 내 공고가 함께 쓴다
set search_path to readme;

alter table postings
  add column analysis            jsonb,        -- 공고 분석 { must, nice, duties, keywords, questions }. 아직 없으면 null
  add column analysis_started_at timestamptz;  -- 돌고 있는 분석이 시작한 시각. 같은 공고를 동시에 두 번 돌리지 않게 잡아 둔다. 끝나면 null
