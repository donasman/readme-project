-- 리드미 V10: 메일 자동화(메일 설계 3절)와 내 공고의 전형 일정(화면·API 대응 3절 15번)
set search_path to readme;

-- 사람마다 수신 주소 하나: <token>@<수신 도메인>. 새로 만들면 token 을 바꾼다(앞 주소는 바로 받지 않는다)
create table mail_inboxes (
  user_id    bigint      primary key references users(id) on delete cascade,
  token      varchar(32) not null unique,   -- 소문자와 숫자 16자. 받을 때 소문자로 찾는다
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- 사람마다 보내는 주소(여기 적힌 주소에서 온 메일만 받는다). 10개까지는 서버가 센다
create table mail_senders (
  user_id    bigint       not null references users(id) on delete cascade,
  email      varchar(320) not null,          -- 소문자
  created_at timestamptz  not null default now(),
  primary key (user_id, email)
);

-- 받은 메일 한 통과 읽어 낸 칸. 원문은 두지 않는다. 본문 글(body)은 분류할 때까지만 둔다
create table inbound_mails (
  id                 bigserial    primary key,
  user_id            bigint       not null references users(id) on delete cascade,
  message_key        char(64)     not null,   -- Message-ID(없으면 원문)의 SHA-256. 같은 메일을 두 번 받지 않는다
  sender             varchar(320) not null,   -- 보낸 곳: From 의 이름, 없으면 주소
  subject            varchar(500) not null,
  received_at        timestamptz  not null,
  status             varchar(12)  not null
    check (status in ('WAITING', 'READING', 'READ', 'FORWARDING')),
  reading_started_at timestamptz,             -- READING 을 잡은 시각. 오래되면 멈춘 것으로 보고 다시 잡는다
  body               text,                    -- 분류 전의 본문 글. 분류하면 null
  code               varchar(20),             -- Gmail 전달 확인 코드(FORWARDING)
  posting_id         bigint       references postings(id) on delete cascade,
  added              boolean      not null default false,  -- 이 메일이 그 공고를 내 공고에 새로 넣었다
  reverted           boolean      not null default false,  -- 새로 넣은 것을 되돌렸다
  review             varchar(10)  check (review in ('PENDING', 'APPLIED', 'IGNORED')),  -- 확인 필요. 제안이 없으면 null
  stage              varchar(12)
    check (stage in ('DOCUMENT', 'TEST', 'INTERVIEW_1', 'INTERVIEW_2', 'FINAL')),     -- 제안한 단계
  result             varchar(12)
    check (result in ('IN_PROGRESS', 'PASSED', 'FAILED')),                         -- 제안한 결과
  schedule_date      date,                    -- 제안한 일정(그 단계의 날짜)
  schedule_time      time,
  quote              varchar(500),            -- 근거 문장. 본문에 그대로 있을 때만
  summary            varchar(100),            -- 확인함과 홈에 보일 짧은 이름
  unique (user_id, message_key),
  check ((stage is null) = (result is null)),
  check (review is null or stage is not null),
  check (schedule_date is not null or schedule_time is null)
);
create index inbound_mails_user_idx on inbound_mails (user_id, received_at desc);
create index inbound_mails_waiting_idx on inbound_mails (status) where status in ('WAITING', 'READING');

-- 내 공고의 전형 일정. 단계마다 날짜 하나. 내 공고 행을 지우면 함께 지워진다
create table user_posting_schedules (
  user_id    bigint      not null,
  posting_id bigint      not null,
  stage      varchar(12) not null
    check (stage in ('DOCUMENT', 'TEST', 'INTERVIEW_1', 'INTERVIEW_2', 'FINAL')),
  on_date    date        not null,
  at_time    time,
  updated_at timestamptz not null default now(),
  primary key (user_id, posting_id, stage),
  foreign key (user_id, posting_id) references user_postings (user_id, posting_id) on delete cascade
);
