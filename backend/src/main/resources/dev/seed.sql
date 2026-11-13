-- 개발용 사용자와 예시 그룹·공고. dev 프로필에서 DevSeedData 가 빈 DB 에 한 번 넣는다. 운영 DB 에는 들어가지 않는다.
--
-- 프런트의 예시 데이터(frontend/src/mocks/example-data.ts)와 같은 사람, 그룹, 공고다. 이름과 회사는 모두 지어낸 예시다.
-- 날짜는 오늘(한국 날짜)을 기준으로 잡는다. 오늘이 2026-11-13 이면 프런트 예시와 같은 날짜가 된다.
-- 임시 로그인: POST /api/auth/dev-login { "email": "daon@example.com" }. 이메일은 모두 <영문 이름>@example.com 이다.
--
-- 프런트 예시와 다른 곳
-- - 나래는 백엔드 스터디에서 "단계·결과까지"를 연다. 그래서 be1 에 "1차 면접 단계"가 보인다(프런트 예시는 "면접 단계").
-- - 댓글은 be1 의 세 개만 글이 있고, 나머지는 댓글 수만 맞춘 "예시 댓글 n"이다.

-- ── 사람 ─────────────────────────────────────────────────────────────
insert into users (email, display_name, email_verified_at) values
  ('daon@example.com',     '다온',   now()),
  ('garam@example.com',    '가람',   now()),
  ('narae@example.com',    '나래',   now()),
  ('raon@example.com',     '라온',   now()),
  ('maru@example.com',     '마루',   now()),
  ('bada@example.com',     '바다',   now()),
  ('sarang@example.com',   '사랑',   now()),
  ('jaram@example.com',    '자람',   now()),
  ('chaoreum@example.com', '차오름', now());

-- ── 그룹과 멤버 ───────────────────────────────────────────────────────
-- ord: 이 순서로 id 를 받는다. 다온에게 백엔드 스터디가 첫 그룹이 된다(프런트 예시처럼 / 가 이 그룹으로 간다).
insert into groups (name, mark, created_by)
select v.name, v.mark, u.id
  from (values (1, '백엔드 스터디', '백', 'garam'),
               (2, '공기업 전산직', '공', 'narae'),
               (3, 'MES 준비반',    'M',  'daon')) as v(ord, name, mark, leader)
  join users u on u.email = v.leader || '@example.com'
 order by v.ord;

insert into group_members (group_id, user_id, role, disclosure)
select g.id, u.id, v.role, v.disclosure
  from (values ('백엔드 스터디', 'daon',     'MEMBER', 'APPLIED'),
               ('백엔드 스터디', 'garam',    'LEADER', 'APPLIED'),
               ('백엔드 스터디', 'narae',    'MEMBER', 'PROGRESS'),
               ('백엔드 스터디', 'raon',     'MEMBER', 'APPLIED'),
               ('백엔드 스터디', 'maru',     'MEMBER', 'APPLIED'),
               ('공기업 전산직', 'daon',     'MEMBER', 'APPLIED'),
               ('공기업 전산직', 'narae',    'LEADER', 'APPLIED'),
               ('공기업 전산직', 'bada',     'MEMBER', 'APPLIED'),
               ('공기업 전산직', 'sarang',   'MEMBER', 'APPLIED'),
               ('MES 준비반',    'daon',     'LEADER', 'APPLIED'),
               ('MES 준비반',    'jaram',    'MEMBER', 'APPLIED'),
               ('MES 준비반',    'chaoreum', 'MEMBER', 'APPLIED')) as v(group_name, person, role, disclosure)
  join groups g on g.name = v.group_name
  join users u on u.email = v.person || '@example.com';

-- ── 공고 본체 ─────────────────────────────────────────────────────────
-- due: 마감일이 오늘부터 며칠 뒤인지(음수면 지남). posting_key 'dev:<키>' 로 아래에서 찾는다.
insert into postings (posting_key, company, title, deadline_kind, deadline_date, deadline_name, apply_at, source_url, sections, created_by)
select 'dev:' || v.key, v.company, v.title, v.kind,
       case when v.kind = 'DATE' then t.today + v.due end,
       v.deadline_name, v.apply_at, v.source_url, v.sections::jsonb, u.id
  from (values
    ('be1',  '예시 커머스',   '백엔드 개발자 신입', 'DATE',    3,    'garam',  '서류 접수 마감', '회사 채용 홈페이지', 'https://example.com/jobs/be1',
     '[{"title": "주요 업무", "items": ["주문·결제 서버 API 개발과 운영", "배치 작업과 내부 관리 도구 개발"]},
       {"title": "필수 요건", "items": ["Java 또는 Kotlin으로 서버를 개발해 본 경험", "관계형 DB를 써 본 경험"]},
       {"title": "우대 사항", "items": ["캐시나 메시지 큐를 써 본 경험", "테스트 코드를 작성해 온 경험"]}]'),
    ('be2',  '예시 핀테크',   '서버 개발 인턴',     'DATE',    7,    'narae',  null, null, null, '[]'),
    ('be3',  '예시 물류',     '백엔드 주니어',      'DATE',    12,   'raon',   null, null, null, '[]'),
    ('be4',  '예시 게임',     '플랫폼 서버 신입',   'DATE',    20,   'maru',   null, null, null, '[]'),
    ('be5',  '예시 에듀테크', '백엔드 엔지니어',    'UNKNOWN', null, 'daon',   null, null, null, '[]'),
    ('be6',  '예시 SaaS',     '서버 개발자',        'ALWAYS',  null, 'garam',  null, null, null, '[]'),
    ('be7',  '예시 모빌리티', '백엔드 신입 공채',   'DATE',    -3,   'narae',  null, null, null, '[]'),
    ('be8',  '예시 보안',     '주니어 서버 개발',   'DATE',    -7,   'garam',  null, null, null, '[]'),
    ('pub1', '예시 공사',     '전산직 7급 공채',    'DATE',    5,    'narae',  null, null, null, '[]'),
    ('pub2', '예시 공단',     '정보보호 신입',      'DATE',    9,    'bada',   null, null, null, '[]'),
    ('pub3', '예시 대학교',   '전산직 직원',        'DATE',    16,   'sarang', null, null, null, '[]'),
    ('mes1', '예시 제조',     'MES 개발 신입',      'DATE',    2,    'daon',   null, null, null, '[]'),
    ('mes2', '예시 자동화',   '스마트팩토리 SW',    'DATE',    14,   'jaram',  null, null, null, '[]')
  ) as v(key, company, title, kind, due, person, deadline_name, apply_at, source_url, sections)
  cross join (select (now() at time zone 'Asia/Seoul')::date as today) t
  join users u on u.email = v.person || '@example.com'
 order by v.key;

insert into posting_keywords (posting_id, keyword, seq)
select p.id, v.keyword, v.seq
  from (values ('Java', 1), ('Kotlin', 2), ('Spring Boot', 3), ('JPA', 4), ('Redis', 5)) as v(keyword, seq)
  join postings p on p.posting_key = 'dev:be1';

-- ── 그룹 공고 ─────────────────────────────────────────────────────────
-- posted: 올린 날이 오늘부터 며칠 전인지. 그날 0시(한국 시각)에 올린 것으로 둔다.
insert into group_posts (group_id, posting_id, posted_by, posted_at)
select g.id, p.id, p.created_by, (t.today + v.posted)::timestamp at time zone 'Asia/Seoul'
  from (values ('백엔드 스터디', 'be1', -8), ('백엔드 스터디', 'be2', -2), ('백엔드 스터디', 'be3', -5),
               ('백엔드 스터디', 'be4', -1), ('백엔드 스터디', 'be5', 0),  ('백엔드 스터디', 'be6', -11),
               ('백엔드 스터디', 'be7', -16), ('백엔드 스터디', 'be8', -19),
               ('공기업 전산직', 'pub1', -9), ('공기업 전산직', 'pub2', -1), ('공기업 전산직', 'pub3', -6),
               ('MES 준비반',    'mes1', -10), ('MES 준비반',   'mes2', -3)) as v(group_name, key, posted)
  cross join (select (now() at time zone 'Asia/Seoul')::date as today) t
  join groups g on g.name = v.group_name
  join postings p on p.posting_key = 'dev:' || v.key;

-- ── 관심과 지원 상태 ──────────────────────────────────────────────────
insert into group_post_interests (group_id, posting_id, user_id)
select gp.group_id, gp.posting_id, u.id
  from (values ('be1', 'garam'), ('be1', 'narae'), ('be1', 'raon'), ('be1', 'daon'),
               ('be2', 'garam'), ('be2', 'narae'), ('be2', 'maru'),
               ('be3', 'raon'),  ('be3', 'daon'),
               ('be4', 'maru'),
               ('be6', 'garam'),
               ('be7', 'garam'), ('be7', 'narae'), ('be7', 'daon'),
               ('be8', 'garam'),
               ('pub1', 'narae'), ('pub1', 'bada'), ('pub1', 'sarang'), ('pub1', 'daon'),
               ('pub2', 'bada'),
               ('pub3', 'sarang'), ('pub3', 'narae'),
               ('mes1', 'jaram'), ('mes1', 'chaoreum'), ('mes1', 'daon'),
               ('mes2', 'jaram'), ('mes2', 'daon')) as v(key, person)
  join postings p on p.posting_key = 'dev:' || v.key
  join group_posts gp on gp.posting_id = p.id
  join users u on u.email = v.person || '@example.com';

-- 관심을 켜면 내 공고 행이 생긴다(미정). 그중 지원한 것만 아래에서 지원함으로 바꾼다.
insert into user_postings (user_id, posting_id)
select distinct i.user_id, i.posting_id
  from group_post_interests i
  join postings p on p.id = i.posting_id
 where p.posting_key like 'dev:%';

update user_postings up
   set decision = 'APPLIED', stage = v.stage, result = 'IN_PROGRESS'
  from (values ('be1', 'garam', 'DOCUMENT'), ('be1', 'narae', 'INTERVIEW_1'),
               ('be2', 'garam', 'DOCUMENT'),
               ('be3', 'daon',  'DOCUMENT'),
               ('be7', 'garam', 'DOCUMENT'), ('be7', 'narae', 'DOCUMENT'), ('be7', 'daon', 'TEST'),
               ('pub1', 'narae', 'DOCUMENT'), ('pub1', 'bada', 'DOCUMENT'),
               ('mes1', 'jaram', 'DOCUMENT'), ('mes1', 'daon', 'DOCUMENT')) as v(key, person, stage)
  join postings p on p.posting_key = 'dev:' || v.key
  join users u on u.email = v.person || '@example.com'
 where up.user_id = u.id and up.posting_id = p.id;

-- ── 댓글 ─────────────────────────────────────────────────────────────
insert into group_post_comments (group_id, posting_id, author_id, body, created_at, updated_at)
select gp.group_id, gp.posting_id, u.id, v.body, w.created, w.created
  from (values ('garam', '코딩 테스트 일정이 공고 아래쪽에 따로 적혀 있어요. 접수 마감 바로 다음 주입니다.', -8),
               ('narae', '작년 지원 후기를 찾았어요. https://example.com/review', -4),
               ('daon',  '포트폴리오 링크 칸이 필수라서 미리 정리해 두면 좋겠어요.', -1)) as v(person, body, day)
  cross join (select (now() at time zone 'Asia/Seoul')::date as today) t
  cross join lateral (select ((t.today + v.day) + time '12:00') at time zone 'Asia/Seoul' as created) w
  join postings p on p.posting_key = 'dev:be1'
  join group_posts gp on gp.posting_id = p.id
  join users u on u.email = v.person || '@example.com';

insert into group_post_comments (group_id, posting_id, author_id, body, created_at, updated_at)
select gp.group_id, gp.posting_id, gp.posted_by, '예시 댓글 ' || n, gp.posted_at + n * interval '1 hour',
       gp.posted_at + n * interval '1 hour'
  from (values ('be2', 1), ('be6', 2), ('be7', 5), ('pub1', 4), ('pub3', 1), ('mes1', 2)) as v(key, comment_count)
  join postings p on p.posting_key = 'dev:' || v.key
  join group_posts gp on gp.posting_id = p.id
  cross join lateral generate_series(1, v.comment_count) as n;
