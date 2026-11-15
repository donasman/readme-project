set search_path to readme;

-- 기본 프로필(docs/리드미_설계_프로필.md 3.1). 사람마다 한 벌이고 나만 본다.
-- 년월은 'YYYY-MM' 글자다. 이력서는 대개 일을 적지 않고, 글자 순서가 곧 시간 순서다.

create table profiles (
  user_id     bigint      primary key references users(id) on delete cascade,
  name        varchar(50),                         -- 이력서의 이름. 표시 이름(users.display_name)과 따로다
  birth_date  date,
  gender      varchar(10) check (gender in ('MALE', 'FEMALE')),
  updated_at  timestamptz not null default now()
);

-- 아래 넷은 여러 줄이다. seq 는 넣은 순서(0부터)이고, 프로필을 고치면 모두 바꿔 넣는다.
create table profile_educations (
  user_id     bigint       not null references profiles(user_id) on delete cascade,
  seq         integer      not null,
  school      varchar(100) not null,
  major       varchar(100),
  graduation  varchar(7)   check (graduation ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),   -- 졸업(예정) 년월
  gpa         numeric(5,2) check (gpa >= 0),
  gpa_scale   numeric(5,2) check (gpa_scale > 0),                              -- 4.5, 4.3, 100 등
  primary key (user_id, seq),
  check (gpa is null or gpa_scale is null or gpa <= gpa_scale)
);

create table profile_certificates (
  user_id     bigint       not null references profiles(user_id) on delete cascade,
  seq         integer      not null,
  name        varchar(100) not null,               -- 자격증 사전(certificates.yaml)의 대표 표기. 사전에 없으면 넣은 그대로
  acquired    varchar(7)   check (acquired ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
  primary key (user_id, seq)
);

create table profile_languages (
  user_id     bigint       not null references profiles(user_id) on delete cascade,
  seq         integer      not null,
  test        varchar(50)  not null,               -- TOEIC, OPIc 등
  score       varchar(20)  not null,               -- 900, IH 처럼 시험마다 꼴이 달라 글자로 둔다
  taken       varchar(7)   check (taken ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
  primary key (user_id, seq)
);

create table profile_careers (
  user_id     bigint       not null references profiles(user_id) on delete cascade,
  seq         integer      not null,
  company     varchar(100) not null,
  start_month varchar(7)   not null check (start_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
  end_month   varchar(7)   check (end_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),   -- null 이면 재직 중
  role        varchar(100),                        -- 직무
  primary key (user_id, seq),
  check (end_month is null or end_month >= start_month)
);
