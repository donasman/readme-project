# 리드미

취업준비생을 위한 정보공유·스터디 웹 서비스. 서버와 화면을 한 저장소에서 관리한다.

```
readme/
├─ backend/            Spring Boot 4.1.2 · Java 21 · Gradle        → 배포: AWS(초안)
├─ frontend/           Vite · React · TypeScript · Tailwind CSS    → 배포: Cloudflare Pages
├─ docs/               설계 초안, 배포 계획, 조사 정리, 화면 디자인(design)
├─ reverse-engineering/ devs7ud.io 디자인 분석, 디자인 적용 계획
├─ deploy/             서버에 올릴 때 쓰는 파일 초안(Caddy, systemd)
├─ .github/workflows/  CI: backend·frontend 빌드 확인(main·dev 푸시, PR)
└─ compose.yaml        개발용 PostgreSQL 16 + 메일 수신기(Mailpit)
```

## 필요한 것

| 도구 | 버전 | 쓰는 곳 |
|---|---|---|
| JDK | 21 | 서버 빌드·실행. 다른 버전만 있으면 Gradle 이 21을 찾지 못해 멈춘다 |
| Node.js | 24(`frontend/.nvmrc`). 22.12 이상이면 빌드된다 | 화면 빌드·실행 |
| Docker Desktop | | 개발용 DB, 서버 테스트 |

## 처음 띄우기

터미널 세 개를 쓴다. 모두 이 폴더(`C:\Dev\readme`)에서 시작한다.

```powershell
# 1) DB(localhost:5433) 와 메일 수신기
docker compose up -d

# 2) 서버 → http://localhost:8080
cd backend
.\gradlew bootRun

# 3) 화면 → http://localhost:5173
cd frontend
npm install
npm run dev:server   # 서버를 부른다. 서버 없이 가짜 서버로만 보려면 npm run dev
```

잘 떴는지 보는 법:

- `http://localhost:8080/actuator/health` 가 `{"status":"UP"}` 을 돌려준다. 서버가 뜰 때 Flyway 가 테이블 21개를 만들므로, 서버가 떴다면 DB 연결과 테이블 생성까지 된 것이다.
- 서버는 dev 프로필로 뜨고, 빈 DB 에 개발용 사용자와 예시 그룹·공고를 넣는다. 예시 사용자는 개발용 비밀번호로 로그인한다: `POST /api/auth/login` 에 `{ "email": "daon@example.com", "password": "readme1234" }`(`docs/리드미_백엔드_구조.md` 6.2).
- `http://localhost:5173` 은 로그인하지 않았으면 랜딩(`/welcome`)을 보인다. 예시 사용자로 로그인하면 홈(`/`)이 열린다. `npm run dev:server` 에서는 카드 추출과 메일(확인함, 자동화)을 빼고 모두 서버를 부른다(아래 "지금 들어 있는 것"의 frontend). `npm run dev` 는 서버 없이 가짜 서버만 부르고, 예시 사용자로 로그인한 채 시작한다.
- 보낸 메일(가입 확인, 비밀번호 재설정)은 `http://localhost:8025` 에서 본다.

## 빌드와 테스트

```powershell
cd backend;  .\gradlew build      # 테스트 포함. Docker 가 켜져 있어야 한다(Testcontainers). 테스트 없이: .\gradlew build -x test
cd frontend; npm run typecheck; npm run lint; npm test; npm run build
```

## 브랜치

| 브랜치 | 뜻 |
|---|---|
| `main` | 배포할 수 있는 상태. `dev`에서만 들어온다 |
| `dev` | 작업을 모으는 곳. 작업 브랜치에서만 들어온다 |
| `종류/영역-내용` | 작업 브랜치. `dev`에서 따고 `dev`로 돌아간다(예: `feat/backend-groups`, `fix/frontend-deadline`) |

새 PC에서 시작하기:

```powershell
git clone https://github.com/donasman/readme-project.git readme
cd readme
git switch dev                         # 원격 dev를 따라가는 로컬 dev가 생긴다
git switch -c feat/frontend-something  # 작업 브랜치
```

병합, 푸시, 브랜치 지우기 규칙은 루트의 `CLAUDE.md`에 있다.

## 지금 들어 있는 것

**backend** (`com.readme`)

- 작업 규칙: `backend/CLAUDE.md`.
- 설정: 세션 쿠키 로그인의 바탕(Spring Session JDBC), CORS, CSRF, 로그인 전 요청에 401, `/actuator/health`. 로그인 전 요청은 세션에 저장하지 않는다(세션 행이 생기지 않는다).
- 오류 응답: 모든 오류가 `{ code, message }` 다(`common/error`). 공통 코드는 `docs/리드미_백엔드_구조.md` 5.2, 기능 코드는 각 설계 문서.
- 시간: 한국 날짜의 기준 `Clock`(`common/config/TimeConfig`, `Asia/Seoul`).
- DB: `V1__init.sql`(계정·경험카드 테이블 10개), `V2__spring_session.sql`(세션 테이블 2개), `V3__groups.sql`(그룹·그룹 공고 테이블 9개), `V4__user_posting_memo.sql`(내 공고의 메모 칸). 스키마는 `readme`.
- 프로필: `dev`(기본, compose.yaml 의 DB `localhost:5433`, 메일 수신기, 예시 데이터), `prod`(환경 변수, `backend/.env.example`).
- API(2026-11-15): 이메일·비밀번호 로그인 8개, 내 계정 5개, 구글 로그인 주소 2개(`docs/리드미_설계_로그인.md`), 그룹 9개(`docs/리드미_설계_그룹.md`), 그룹 공고 11개, 내 공고 5개, 공고 읽어 오기 1개(`docs/리드미_설계_공고.md`), 경험카드 8개, 자료 3개(`docs/리드미_설계_경험카드.md`). 목록은 `docs/리드미_백엔드_구조.md` 9절. 가입·이메일 인증·로그인·로그아웃·비밀번호 재설정, 이름·비밀번호 바꾸기, 탈퇴(그룹장 넘기기), 그룹 목록·만들기·공개 범위·나가기·초대 링크·초대 미리 보기·초대 목록·취소·수락, 그룹 공고 목록·상세·올리기·고치기·내리기·날짜 넣기·관심·댓글, 홈의 그룹 소식, 내 공고 목록·상세·넣기(그룹 없이)·상태·메모, 경험카드 목록·상세·만들기·고치기·지우기·확인·태그, 자료 올리기·목록·지우기, 공고 링크·본문·파일 읽어 오기와 키워드. 그룹 공고 올리기와 내 공고 넣기는 키워드와 공고 내용 묶음도 받는다. 같은 공고는 jobfit 공고키로 알아본다(`posting/PostingKeys`). 키워드는 키워드 사전으로 찾는다(`keyword/KeywordDictionary`).
- 로그인: 이메일·비밀번호 로그인(2026-11-14)과 구글 로그인(2026-11-15)이 있다. 구글 로그인은 구글 클라이언트 값을 넣어야 켜진다(`docs/리드미_설계_로그인.md` 6.1). 로그인 실패와 메일 보내기에 횟수 제한이 있다. dev 프로필의 임시 로그인은 걷어냈다.
- 패키지(`docs/리드미_백엔드_구조.md` 3절): `auth`(계정, 로그인, 세션, `/api/me`), `group`, `posting`, `grouppost`, `postingread`(공고 읽어 오기), `card`, `extraction`(자료 올리기와 공고 파일의 글 읽기. PDF·HWP는 따로 띄운 자바 프로세스에서 읽는다), `keyword`, `dev`(예시 데이터), `common`. `llm`은 설명만 있다.
- 테스트: `@IntegrationTest`(서버 전체 + Testcontainers PostgreSQL, 테스트끼리 나눠 씀, 시계는 2026-11-13 00:30 서울에 고정, 메일은 `TestMailbox` 에 모음). 로그인·가입·재설정, 권한·공개 범위·상태 글자·날짜 규칙, 세션, 오류 응답.
- 자원: `resources/dictionary`(키워드 사전 4개), `resources/prompts/exp-extract.md`(jobfit 원본 그대로. 옮길 때 첫 줄의 jobfit 안내와 변수 표기를 고친다).

**frontend** (2026-11-15)

- 화면: 시안이 나온 25개와 창 7개가 모두 있다(`docs/리드미_화면_API_대응.md` 1절). 주소는 `src/shared/lib/paths.ts` 에 모여 있다.
  - 로그인 전: 랜딩 `/welcome`, 로그인 `/login`, 회원가입 `/signup`·`/signup/sent`, 이메일 인증 `/verify-email`, 비밀번호 재설정 `/password-reset`·`/password-reset/sent`·`/password-reset/new`, 초대 `/invite/:token`
  - 홈·지원 관리: 홈 `/`, 내 공고 `/my-posts`·`/my-posts/:postingId`, 지원 현황 `/board`, 달력 `/calendar`, 메일 확인함 `/mail`
  - 그룹: 내 그룹 `/groups`, 그룹 공고 목록·상세 `/groups/:groupId/posts`·`…/posts/:postId`, 공고 올리기와 칸 확인 `…/posts/new`·`…/posts/new/check`, 멤버·초대 관리 `…/members`
  - 경험카드: 목록 `/cards`, 직접 만들기 `/cards/new`, 상세·편집 `/cards/:cardId`·`/cards/:cardId/edit`, 자료로 카드 만들기 `/cards/sources`, 카드 확인 `/cards/review`
  - 설정: 내 정보 `/settings/account`, 메일 자동화 `/settings/mail`, 탈퇴 `/settings/withdraw`
  - `/` 는 홈이다. 로그인하지 않았으면 랜딩으로, 그룹이 없으면 내 그룹으로 보낸다. 다른 화면은 로그인하지 않았으면 로그인 화면으로 보낸다. `/dev/components` 는 부품 확인용이고 개발 서버에서만 열린다.
- 화면의 기준: 그룹 공고 두 화면은 `docs/design/`(디자인 원본과 캡처), 나머지는 캔버스 "리드미 시안 3종"의 1~9차 시안이다. 화면마다 어느 차수인지는 `docs/리드미_화면_API_대응.md` 1절에 있다.
- 구조: `src/app`(라우터, 틀, 헤더), `src/features/<기능>`(`auth`, `cards`, `group-posts`, `groups`, `mail`, `my-posts`, `settings`), `src/domain`(`session`, `group`, `posting`), `src/shared`(공용 부품과 `lib`), `src/mocks`(가짜 서버). 규칙은 `frontend/CLAUDE.md`, 근거는 `docs/리드미_프런트_구조_계획.md`.
- 데이터: 각 층의 `api.ts` 가 데이터를 읽고 고치는 한 곳이다. 서버는 `src/shared/lib/api.ts` 의 `request` 로 부른다(쿠키 포함, CSRF 헤더, 오류는 `ApiError`). `npm run dev` 는 모두 가짜 서버(`src/mocks`, 예시 데이터를 메모리에 둔다)를 부르고, `npm run dev:server` 와 빌드는 아래의 서버를 부른다. 서버 주소는 `VITE_API_ORIGIN`(없으면 `http://localhost:8080`)이고 개발 프록시는 두지 않았다.
- 서버를 부르는 것: 로그인 쪽 화면 모두(로그인, 회원가입, 이메일 인증, 비밀번호 재설정, 구글 로그인 시작), 보는 사람(`GET /api/me`), 초대, 그룹과 멤버·초대 관리, 그룹 공고(읽어 오기의 링크·본문·파일·키워드, 올리기의 키워드·묶음, 목록의 원문 주소, 관심, 댓글, 마감일, 내리기 포함), 홈, 내 공고, 지원 현황, 달력, 경험카드(목록, 직접 만들기, 상세·편집, 확인, 지우기, 태그 제안), 자료(올리기, 목록, 지우기), 내 정보, 이름 바꾸기, 로그아웃, 탈퇴.
- 아직 가짜 서버만 부르는 것: 메일 확인함, 메일 자동화 설정, 홈의 "메일에서 온 변경". `npm run dev:server` 에서도 이 자리는 예시 데이터를 보인다. 카드 추출은 서버에 없어 `npm run dev:server` 에서 "카드 뽑기 시작"을 누르면 준비 중이라고 알린다. 메일 자동화도 서버에 없다. 화면이 바라는데 서버에 없는 칸은 `docs/리드미_화면_API_대응.md` 1절과 3절에 있다.
- 서버에 있는데 아직 쓰지 않는 것: 그룹 없이 내 공고에 넣기, 공고 고치기, 살아 있는 초대 목록·취소, 로그인한 채로 비밀번호 바꾸기. 넷 다 시안이 없어 시안을 먼저 만든다(`docs/리드미_남은작업.md` 프런트엔드 1단계).
- 동작이 없는 자리: 헤더의 정보공유(화면이 없다), 내 공고의 공고 넣기(서버 API는 있지만 그룹 없이 넣는 화면이 없다), 설정의 구글 연결(서버에 없다). 원문 주소가 없는 공고는 원문 보기·열기를 감춘다. 이런 자리와 나중에 고칠 것은 코드에 `shortcut:` 으로 적혀 있다.
- 검사: `npm run typecheck`, `npm run lint`, `npm test`(Vitest), `npm run build`. 모양이 바뀌면 안 되는 변경은 화면 회귀 검사(`npm run regress`, 장면 69개)로 본다. 방법은 `frontend/CLAUDE.md` 에 있다.
- `src/index.css`: Arc 변수(`src/components/arc/foundation.css`) 위에 리드미 값(색 A)을 덮고, shadcn/ui 색 이름을 Arc 변수에 연결한다.
- 부품: shadcn/ui 는 `src/components/ui`(button, input, textarea, dialog, dropdown-menu), dsui 는 `src/components/arc`(segmented-control, app-header 와 함께 들어온 기반). 헤더는 `src/app/layouts/group-header.tsx`(그룹 화면)와 `main-header.tsx`(그룹 밖 화면)를 따로 만들어 쓴다. 정한 값은 `reverse-engineering/리드미_디자인_적용_계획.md` 의 "적용 결과"에 있다.

## 확인한 것과 못 한 것 (2026-11-12. 뒤에 다시 본 것은 그 날짜를 적었다)

| 대상 | 상태 |
|---|---|
| frontend | (2026-11-15) `dev` `502aad9`에서 `npm run typecheck`, `npm run lint`, `npm test`(테스트 99개), `npm run build` 통과, 화면 회귀 검사 69장면이 전과 픽셀까지 같다. 같은 날 `npm run dev:server`로 서버에 붙인 화면을 진짜 서버에서 눌러 보았다. (2026-11-15) `dev` `e72e0d2`에서 `npm ci` 뒤 같은 검사 통과(테스트 94개). (2026-11-13) `npm run typecheck`, `npm run lint`, `npm run build` 통과. 그룹 공고 목록·상세를 1280px·390px, 라이트·다크로 띄워 `docs/design/shots` 와 비교했고 픽셀 차이는 0.1~0.4% |
| DB 마이그레이션 | (2026-11-13) Flyway 가 V1, V2 를 compose 의 PostgreSQL 16 과 Testcontainers 의 PostgreSQL 16 에 적용했다. V3 도 같은 날 Flyway 로 적용했다(`docs/리드미_설계_그룹.md`, `docs/리드미_설계_공고.md` 3.3절) |
| backend | (2026-11-15) 작업 브랜치 11개를 `dev`에 넣은 뒤 `.\gradlew build` 통과(테스트 752개). 같은 날 5개(내 공고 넣기, 올리기의 키워드·묶음, 읽어 오기의 키워드, 읽어 오기의 파일, 목록의 원문 주소)를 더 넣은 뒤 다시 통과(테스트 796개, `3034097`). (2026-11-13) Windows PC(JDK 21, Gradle 8.14.3)에서 `.\gradlew build` 통과. `bootRun` 으로 띄워 health `UP`, 로그인 전 401, CSRF 토큰 없는 요청 403, `localhost:5173` 만 받는 CORS, `XSRF-TOKEN` 쿠키 발급을 확인했다 |
| shadcn/ui, dsui | shadcn 프리셋(`base-nova`)으로 `frontend` 를 새로 만들고 부품을 받아 두었다. 위 frontend 줄의 빌드와 화면 비교에 포함된다 |
| GitHub Actions, deploy | (2026-11-15) GitHub Actions 는 `dev`에 올릴 때마다 backend·frontend 가 돌고 통과한다. deploy 는 돌려 보지 못했다 |

## 다음에 할 일

남은 일 전체는 `docs/리드미_남은작업.md`에 담당(백엔드, 프런트엔드, 운영)과 순서로 나눠 두었다(2026-11-15).

1. 서버: 2026-11-15에 남은 결정을 정하고(각 설계 문서의 "정함"), 구글 로그인, 내 계정·탈퇴, 그룹 초대, 내 공고(그룹 없이 넣기 포함), 그룹 공고 고치기, 경험카드, 자료 올리기, 공고 읽어 오기(링크, 본문, 파일, 키워드)를 만들었다. 서버 쪽 다음 일은 LLM 바탕(`llm` 패키지)이고, 그 위에 카드 추출(LLM)과 카드가 나온 원문 조각, 기본 프로필, 공고 읽어 오기의 LLM 보완(못 읽은 칸, 글자가 없는 파일), 공고 상세의 맞는 카드, 메일 자동화를 올린다. 메일 자동화는 수신 전용 도메인을 산 뒤에 한다(`docs/리드미_리서치_재사용_정리.md` 7절의 도메인 줄). 화면이 요구하는데 서버에 없는 것은 `docs/리드미_화면_API_대응.md` 1절과 3절에 있다.
2. 화면: 시안이 나온 25개를 모두 프런트에 옮겼다(`docs/리드미_화면_API_대응.md` 1절). 2026-11-15에 서버에 API가 있는 화면을 모두 서버에 붙였다(`npm run dev:server`, 띄우는 법은 `frontend/CLAUDE.md`). 남은 것은 서버에 있는데 시안이 없는 넷(내 공고 넣기, 공고 고치기, 초대 목록·취소, 비밀번호 바꾸기)과, 서버에 없는 카드 추출·메일 자동화를 붙이는 것이다. 넷은 시안을 먼저 만든다(시안 3종 캔버스의 10차).
3. 구글 로그인을 켠다. 서버 코드는 있다(2026-11-15). Google Cloud 에서 OAuth 클라이언트를 만들어 `backend/application-local.yml`에 넣는다(`docs/리드미_설계_로그인.md` 6.1).

## 문서

아래 문서는 저장소의 `docs/`가 원본이다(2026-11-15에 정함, `docs/리드미_백엔드_구조.md` 11절). 처음에는 claude.ai 프로젝트 "리드미" 문서의 사본이었다. 프로젝트 문서가 필요하면 `docs/`에서 복사한다.

- `docs/리드미_백엔드_구조.md` 서버의 패키지, 모든 API에 걸리는 규칙과 공통 오류 코드, 로그인과 세션, 예시 데이터, DB, 테스트의 틀, 만든 API (2026-11-15 고침)
- `docs/리드미_설계_로그인.md` 계정 테이블 3개, 로그인 API 13개와 구글 주소 2개, 만들면서 정한 것과 오류 코드, 구글 로그인 (2026-11-15 고침)
- `docs/리드미_설계_경험카드.md` 카드·자료·추출 테이블 7개, API 13개, 만들면서 정한 것, jobfit에서 옮길 것 (2026-11-15 고침)
- `docs/리드미_설계_그룹.md` 그룹 테이블 3개, API 9개 (2026-11-15 고침)
- `docs/리드미_설계_공고.md` 그룹 공고·내 공고 테이블 6개, API 17개, 화면에 없는 로직 점검 (2026-11-15 고침)
- `docs/리드미_설계_로그인_경험카드.md`, `docs/리드미_설계_그룹_공고.md` 2026-11-13에 위 넷으로 나눴다. 옛 절이 어디로 갔는지만 남겼다(코드 주석이 옛 절을 가리킨다)
- `docs/리드미_남은작업.md` 남은 일을 담당(백엔드, 프런트엔드, 운영)과 단계로 나눈 것 (2026-11-15)
- `docs/리드미_설계_프로필_방향.md` 기본 프로필(인적사항, 학력·자격증·어학·경력)의 설계 전 방향과 걸린 것 (2026-11-15)
- `docs/리드미_화면_API_대응.md` 화면 27개의 시안·프런트·서버 상태, 화면별로 쓰는 API, 시안이 요구하는데 설계에 없던 것 25개와 정한 것 (2026-11-15 고침)
- `docs/리드미_배포_계획.md` 구성, 비용, 뼈대에 미리 넣을 것 (2026-11-12 사본)
- `docs/리드미_리서치_재사용_정리.md` 정한 것과 남은 결정 (2026-11-13 사본)
- `docs/리드미_프런트_구조_계획.md` 프런트 폴더의 층, 옮길 파일, 지금 뽑는 공용 부품 (2026-11-13 사본)
- `reverse-engineering/리드미_디자인_적용_계획.md` shadcn/ui 와 dsui 설치 순서, 적용 결과 (2026-11-13 사본)
- `reverse-engineering/리드미_리서치_디자인_devs7udio.md` devs7ud.io 디자인 분석 (2026-11-12 사본)

`docs/design/` 은 사본이 아니라 이 폴더가 원본이다. 그룹 공고 목록·상세의 디자인 원본과 캡처가 있다. 나머지 화면의 시안은 캔버스에만 있다(주소는 `docs/리드미_화면_API_대응.md` 머리에 있다).
