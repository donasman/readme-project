set search_path to readme;

-- 리드미 V13: 내 공고마다 경험카드 우선순위(카드 우선순위 설계, 2026-11-23).
-- 초안은 LLM 이 고르고(이유 한 줄), 그 뒤에는 사용자가 순서를 바꾸고 빼고 더한다. 내 공고를 빼거나 카드를 지우면 함께 지워진다.
create table user_posting_cards (
  user_id    bigint       not null,
  posting_id bigint       not null,
  card_id    bigint       not null references experience_cards(id) on delete cascade,
  position   smallint     not null,              -- 0 이 첫째
  reason     varchar(200),                       -- LLM 이 고른 이유. 사용자가 더한 카드는 null
  primary key (user_id, posting_id, card_id),
  foreign key (user_id, posting_id) references user_postings(user_id, posting_id) on delete cascade
);
create index user_posting_cards_card_idx on user_posting_cards (card_id);
