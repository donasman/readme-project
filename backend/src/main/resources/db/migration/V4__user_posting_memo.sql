-- 리드미 V4: 내 공고의 나만 보는 메모
set search_path to readme;

alter table user_postings add column memo varchar(2000);  -- 나만 보는 메모. 그룹에는 보이지 않는다. 비어 있으면 null
