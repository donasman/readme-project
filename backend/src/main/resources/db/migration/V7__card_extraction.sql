set search_path to readme;

-- 카드 추출(경험카드 설계 4.6, 남은 작업 5단계).

-- 카드가 나온 자료 조각(1부터). 카드 확인 화면에 그 조각(5,000자)을 보여 준다(화면·API 대응 3절 21번).
-- 직접 만든 카드와 추출 전에 만든 카드는 null 이다.
alter table experience_cards add column source_part integer check (source_part >= 1);

-- 이미 있는 카드와 같은 경험이라 건너뛴 카드의 제목들(작업 상태의 skippedSameAs).
alter table card_extraction_jobs add column skipped_titles jsonb not null default '[]';
