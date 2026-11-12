-- 리드미 V1: 로그인 · 경험카드
-- JDBC 주소에 currentSchema=readme 를 붙여 쓴다. Flyway, JPA, Spring Session 이 모두 이 스키마를 본다.
create schema if not exists readme;
set search_path to readme;

-- ── 계정 ─────────────────────────────────────────────────────────────
create table users (
  id                bigint generated always as identity primary key,
  email             varchar(320) not null,
  email_verified_at timestamptz,                 -- null 이면 아직 인증 전
  password_hash     varchar(100),                -- 구글로만 가입한 계정은 null
  display_name      varchar(50)  not null,
  created_at        timestamptz  not null default now(),
  updated_at        timestamptz  not null default now(),
  last_login_at     timestamptz
);
create unique index users_email_key on users (lower(email));

create table user_identities (
  id               bigint generated always as identity primary key,
  user_id          bigint       not null references users(id) on delete cascade,
  provider         varchar(20)  not null check (provider in ('GOOGLE')),
  provider_user_id varchar(255) not null,        -- 구글 ID 토큰의 sub
  provider_email   varchar(320),                 -- 연결할 때 구글이 준 이메일(참고용)
  created_at       timestamptz  not null default now(),
  unique (provider, provider_user_id),
  unique (user_id, provider)
);

create table email_tokens (
  id         bigint generated always as identity primary key,
  user_id    bigint      not null references users(id) on delete cascade,
  purpose    varchar(20) not null check (purpose in ('VERIFY_EMAIL', 'RESET_PASSWORD')),
  token_hash char(64)    not null unique,        -- 토큰 원문은 메일에만 있다. DB에는 SHA-256
  expires_at timestamptz not null,
  used_at    timestamptz,
  created_at timestamptz not null default now()
);
create index email_tokens_user_idx on email_tokens (user_id, purpose);

-- ── 경험카드 ─────────────────────────────────────────────────────────
create table card_sources (
  id           bigint generated always as identity primary key,
  user_id      bigint      not null references users(id) on delete cascade,
  kind         varchar(10) not null check (kind in ('FILE', 'TEXT')),
  file_name    varchar(255),
  content_type varchar(100),
  byte_size    bigint,
  sha256       char(64)    not null,             -- 같은 자료를 다시 올렸는지 판별
  body         text        not null,             -- 읽어 낸 본문. 숫자 원문 대조에 쓴다
  char_count   integer     not null,
  created_at   timestamptz not null default now(),
  unique (user_id, sha256)
);

create table card_extraction_jobs (
  id            bigint generated always as identity primary key,
  user_id       bigint      not null references users(id) on delete cascade,
  source_id     bigint      not null references card_sources(id) on delete cascade,
  status        varchar(10) not null check (status in ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
  total_parts   integer     not null,            -- 본문을 5,000자씩 나눈 조각 수 = LLM 호출 수
  done_parts    integer     not null default 0,
  error_message text,
  created_at    timestamptz not null default now(),
  started_at    timestamptz,
  finished_at   timestamptz
);
create index card_extraction_jobs_user_idx on card_extraction_jobs (user_id, created_at desc);

create table experience_cards (
  id               bigint generated always as identity primary key,
  user_id          bigint       not null references users(id) on delete cascade,
  title            varchar(200) not null,
  period           varchar(50),                  -- '2025-03 ~ 2025-06' 글자 그대로
  org              varchar(200),
  role             varchar(200),
  summary          varchar(200),                 -- 60자를 넘으면 경고만 낸다
  situation        text,
  task             text,
  action           text,
  result           text,
  verified         boolean      not null default false,
  verified_at      timestamptz,
  visibility       varchar(10)  not null default 'PRIVATE' check (visibility in ('PRIVATE')),
  source_id        bigint references card_sources(id) on delete set null,
  job_id           bigint references card_extraction_jobs(id) on delete set null,
  extraction_notes jsonb,                        -- 추출 때 생긴 안내 목록. 카드를 확인하면 비운다
  created_at       timestamptz  not null default now(),
  updated_at       timestamptz  not null default now()
);
create index experience_cards_user_idx on experience_cards (user_id, updated_at desc);

create table card_tags (
  card_id bigint       not null references experience_cards(id) on delete cascade,
  tag     varchar(100) not null,                 -- 키워드 사전의 대표 표기
  primary key (card_id, tag)
);
create index card_tags_tag_idx on card_tags (tag);

create table card_usable_for (
  card_id       bigint      not null references experience_cards(id) on delete cascade,
  question_type varchar(10) not null
    check (question_type in ('지원동기', '직무역량', '협업', '문제해결', '성장', '기타')),
  primary key (card_id, question_type)
);

create table card_facts (
  card_id bigint       not null references experience_cards(id) on delete cascade,
  seq     integer      not null,                 -- 화면에 보이는 순서
  body    varchar(500) not null,
  primary key (card_id, seq)
);

-- ── LLM 사용 기록 ────────────────────────────────────────────────────
create table llm_usage (
  id            bigint generated always as identity primary key,
  user_id       bigint references users(id) on delete set null,
  feature       varchar(30) not null,            -- 'EXP_EXTRACT' 등
  model         varchar(50) not null,
  input_tokens  integer     not null,
  output_tokens integer     not null,
  created_at    timestamptz not null default now()
);
create index llm_usage_user_idx on llm_usage (user_id, created_at);
