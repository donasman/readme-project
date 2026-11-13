-- 리드미 V3: 그룹 · 그룹 공고
set search_path to readme;

-- ── 그룹 ─────────────────────────────────────────────────────────────
create table groups (
  id         bigint generated always as identity primary key,
  name       varchar(50) not null,
  mark       varchar(4)  not null,               -- 헤더 동그라미 안의 글자. 비워 보내면 서버가 이름 첫 글자를 넣는다
  created_by bigint references users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table group_members (
  group_id   bigint      not null references groups(id) on delete cascade,
  user_id    bigint      not null references users(id)  on delete cascade,
  role       varchar(10) not null default 'MEMBER'  check (role in ('LEADER', 'MEMBER')),
  disclosure varchar(10) not null default 'APPLIED' check (disclosure in ('INTEREST', 'APPLIED', 'PROGRESS')),
  joined_at  timestamptz not null default now(),
  primary key (group_id, user_id)
);
create unique index group_members_leader_key on group_members (group_id) where role = 'LEADER';  -- 그룹장은 한 명
create index group_members_user_idx on group_members (user_id);

create table group_invites (
  id         bigint generated always as identity primary key,
  group_id   bigint      not null references groups(id) on delete cascade,
  token_hash char(64)    not null unique,        -- 토큰 원문은 초대 링크에만 있다. DB에는 SHA-256
  created_by bigint references users(id) on delete set null,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  created_at timestamptz not null default now()
);
create index group_invites_group_idx on group_invites (group_id);

-- ── 공고 본체 ─────────────────────────────────────────────────────────
-- 그룹과 무관하게 공고 하나에 한 행. 같은 공고를 여러 그룹이 올리면 이 행을 함께 가리킨다.
create table postings (
  id              bigint generated always as identity primary key,
  posting_key     varchar(300) unique,           -- 같은 공고 판별 키(jobfit 공고키). 만들 수 없으면 null
  company         varchar(200) not null,
  title           varchar(300) not null,
  deadline_kind   varchar(10)  not null check (deadline_kind in ('DATE', 'ALWAYS', 'UNKNOWN')),
  deadline_date   date,
  deadline_name   varchar(50),                   -- 예: '서류 접수 마감'
  apply_at        varchar(100),                  -- 지원처. 예: '회사 채용 홈페이지'
  source_url      varchar(2000),
  sections        jsonb        not null default '[]'::jsonb,  -- 공고 내용 묶음 [{ "title": "주요 업무", "items": ["…"] }]
  created_by      bigint references users(id) on delete set null,
  deadline_set_by bigint references users(id) on delete set null,  -- "날짜 넣기"를 한 사람
  deadline_set_at timestamptz,
  created_at      timestamptz  not null default now(),
  updated_at      timestamptz  not null default now(),
  check ((deadline_kind = 'DATE') = (deadline_date is not null))
);

create table posting_keywords (
  posting_id bigint       not null references postings(id) on delete cascade,
  keyword    varchar(100) not null,              -- 키워드 사전의 대표 표기
  seq        integer      not null,              -- 화면에 보이는 순서
  primary key (posting_id, keyword)
);
create index posting_keywords_keyword_idx on posting_keywords (keyword);

-- ── 그룹 공고 (그룹 × 공고) ───────────────────────────────────────────
create table group_posts (
  group_id      bigint      not null references groups(id)   on delete cascade,
  posting_id    bigint      not null references postings(id) on delete cascade,
  posted_by     bigint references users(id) on delete set null,
  posted_at     timestamptz not null default now(),
  taken_down_at timestamptz,                     -- "그룹에서 내리기". 행은 남기고 목록에서만 뺀다
  taken_down_by bigint references users(id) on delete set null,
  primary key (group_id, posting_id)
);
create index group_posts_posting_idx on group_posts (posting_id);

-- 관심은 그룹별로 기록한다. 멤버 행에 묶여 있어, 그룹을 나가면 그 그룹의 관심이 함께 지워진다.
create table group_post_interests (
  group_id   bigint      not null,
  posting_id bigint      not null,
  user_id    bigint      not null,
  created_at timestamptz not null default now(),
  primary key (group_id, posting_id, user_id),
  foreign key (group_id, posting_id) references group_posts (group_id, posting_id) on delete cascade,
  foreign key (group_id, user_id)    references group_members (group_id, user_id)  on delete cascade
);
create index group_post_interests_member_idx on group_post_interests (group_id, user_id);

-- 댓글은 "그룹 × 공고"에 달린다. 쓴 사람이 그룹을 나가거나 탈퇴해도 댓글은 남는다.
create table group_post_comments (
  id         bigint generated always as identity primary key,
  group_id   bigint        not null,
  posting_id bigint        not null,
  author_id  bigint references users(id) on delete set null,
  body       varchar(2000) not null,             -- 글자 그대로. 링크는 응답을 만들 때 나눈다
  created_at timestamptz   not null default now(),
  updated_at timestamptz   not null default now(),
  foreign key (group_id, posting_id) references group_posts (group_id, posting_id) on delete cascade
);
create index group_post_comments_post_idx on group_post_comments (group_id, posting_id, created_at);

-- ── 내 공고 (지원 상태의 원본) ────────────────────────────────────────
-- 사용자 × 공고에 한 행. 그룹에는 이 값이 공개 범위만큼만 보인다.
create table user_postings (
  user_id    bigint      not null references users(id)    on delete cascade,
  posting_id bigint      not null references postings(id) on delete cascade,
  decision   varchar(10) not null default 'UNDECIDED'
    check (decision in ('UNDECIDED', 'HOLD', 'SKIP', 'APPLIED')),               -- 미정, 보류, 지원안함, 지원함
  stage      varchar(12)
    check (stage in ('DOCUMENT', 'TEST', 'INTERVIEW_1', 'INTERVIEW_2', 'FINAL')), -- 서류, 필기, 1차 면접, 2차 면접, 최종
  result     varchar(12)
    check (result in ('IN_PROGRESS', 'PASSED', 'FAILED')),                       -- 진행 중, 합격, 불합격
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, posting_id),
  check ((decision = 'APPLIED') = (stage is not null)),   -- 단계와 결과는 지원함일 때만 있다
  check ((stage is null) = (result is null))
);
create index user_postings_posting_idx on user_postings (posting_id);
