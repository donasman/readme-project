set search_path to readme;

-- 리드미 V14: 내 공고의 자기소개서 문항과 문항마다 고른 경험카드(문항 매칭 설계, 2026-11-24).

-- 내 공고의 자기소개서 문항. 행이 없고 questions_saved 가 false 면 공고 분석의 문항을 보인다(QD2).
alter table user_postings add column questions_saved boolean not null default false;

create table user_posting_questions (
  id          bigserial    primary key,
  user_id     bigint       not null,
  posting_id  bigint       not null,
  position    smallint     not null,              -- 0 이 첫째
  text        varchar(1000) not null,
  char_limit  integer,                            -- 글자 수. 없으면 null
  type        varchar(20),                        -- LLM 이 고른 문항 유형(QD8). 고르기 전이나 버렸으면 null
  foreign key (user_id, posting_id) references user_postings(user_id, posting_id) on delete cascade
);
create index user_posting_questions_owner_idx on user_posting_questions (user_id, posting_id);

-- 문항마다 고른 카드(QD1, QD3).
create table user_posting_question_cards (
  question_id bigint       not null references user_posting_questions(id) on delete cascade,
  card_id     bigint       not null references experience_cards(id) on delete cascade,
  position    smallint     not null,              -- 0 이 주된 카드
  reason      varchar(200),                       -- LLM 이 고른 이유. 사용자가 더한 카드는 null
  emphasis    varchar(60),                        -- 살릴 칸(QD9), 쉼표로 잇는다. 사용자가 더한 카드는 null
  primary key (question_id, card_id)
);
create index user_posting_question_cards_card_idx on user_posting_question_cards (card_id);
