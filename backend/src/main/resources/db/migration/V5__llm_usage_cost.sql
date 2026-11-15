set search_path to readme;

-- LLM 사용액(남은 작업 2-3·2-4, 경험카드 설계 4.5).
-- 입력·출력 말고도 캐시 쓰기(5분·1시간)와 캐시 읽기의 단가가 따로라 토큰을 나눠 둔다.
-- cost_micros 는 저장할 때의 단가표(app.llm.prices)로 낸 금액이다. 단가가 바뀌어도 지난 행은 그대로이고, 한도는 이 칸의 합으로 본다.
-- 서버 쪽 fallback 이 돌면 시도마다 한 행이다(행마다 그 시도를 한 모델).
alter table llm_usage
  add column cache_write_5m_tokens integer not null default 0,
  add column cache_write_1h_tokens integer not null default 0,
  add column cache_read_tokens     integer not null default 0,
  add column cost_micros           bigint  not null default 0;   -- 백만분의 1달러. 위로 올린다
