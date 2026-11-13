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
npm run dev
```

잘 떴는지 보는 법:

- `http://localhost:8080/actuator/health` 가 `{"status":"UP"}` 을 돌려준다. 서버가 뜰 때 Flyway 가 테이블 21개를 만들므로, 서버가 떴다면 DB 연결과 테이블 생성까지 된 것이다.
- 서버는 dev 프로필로 뜨고, 빈 DB 에 개발용 사용자와 예시 그룹·공고를 넣는다. `POST /api/auth/dev-login` 에 `{ "email": "daon@example.com" }` 을 보내면 그 사람으로 로그인된다(임시 로그인, `backend/CLAUDE.md`).
- `http://localhost:5173` 은 내 첫 그룹의 공고 목록(`/groups/be/posts`)으로 넘어간다. 화면은 아직 서버를 부르지 않고 예시 데이터로 돈다. 서버가 꺼져 있어도 화면은 뜬다.
- 보낸 메일은 `http://localhost:8025` 에서 본다(아직 메일을 보내는 기능은 없다).

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
- 오류 응답: 모든 오류가 `{ code, message }` 다(`common/error`). 코드 목록은 `docs/리드미_설계_그룹_공고.md` 4절.
- 시간: 한국 날짜의 기준 `Clock`(`common/config/TimeConfig`, `Asia/Seoul`).
- DB: `V1__init.sql`(설계 초안의 테이블 10개), `V2__spring_session.sql`(세션 테이블 2개), `V3__groups.sql`(그룹·그룹 공고 테이블 9개). 스키마는 `readme`.
- 프로필: `dev`(기본, compose.yaml 의 DB `localhost:5433`, 예시 데이터, 임시 로그인), `prod`(환경 변수, `backend/.env.example`).
- API: `GET /api/me`, `GET /api/groups`, `GET /api/groups/{groupId}/posts`, `GET …/posts/{postId}`, `PUT …/posts/{postId}/interest`, `POST …/posts/{postId}/comments`. 프런트의 두 화면이 부르는 것이다. 나머지(올리기, 내리기, 날짜 넣기, 초대 등)는 아직 없다.
- 로그인: 실제 로그인은 아직 없다. dev 프로필에만 임시 로그인(`POST /api/auth/dev-login`)이 있고, 실제 로그인과 같은 세션을 만든다.
- 패키지: `auth`(세션, `/api/me`), `group`, `posting`, `grouppost`, `dev`(임시 로그인, 예시 데이터), `common`. `card`, `extraction`, `keyword`, `llm`은 설명만 있다.
- 테스트: `@IntegrationTest`(서버 전체 + Testcontainers PostgreSQL, 테스트끼리 나눠 씀, 시계는 2026-11-13 00:30 서울에 고정). 권한·공개 범위·상태 글자·날짜 규칙, 세션, 오류 응답.
- 자원: `resources/dictionary`(키워드 사전 4개), `resources/prompts/exp-extract.md`(jobfit 원본 그대로. 옮길 때 첫 줄의 jobfit 안내와 변수 표기를 고친다).

**frontend** (2026-11-13)

- 화면 7개: 로그인 `/login`, 회원가입 `/signup`, 이메일 인증 `/signup/sent`·`/verify-email`, 비밀번호 재설정 `/password-reset`·`/password-reset/sent`·`/password-reset/new`, 경험카드 목록 `/cards`, 그룹 공고 목록 `/groups/:groupId/posts`, 그룹 공고 상세 `/groups/:groupId/posts/:postId`. `/` 는 내 첫 그룹의 목록으로 보낸다. `/dev/components` 는 부품 확인용이고 개발 서버에서만 열린다.
- 화면의 기준: 그룹 공고 두 화면은 `docs/design/`(디자인 원본과 캡처), 나머지는 캔버스 "리드미 시안 3종"의 시안이다(로그인과 경험카드 목록은 1차의 A 시안, 가입·인증·재설정은 5차). 시안이 나온 화면 25개와 프런트에 옮긴 화면은 `docs/리드미_화면_API_대응.md` 1절에 있다.
- 구조: `src/app`(라우터, 틀, 헤더), `src/features/<기능>`(`auth`, `cards`, `group-posts`), `src/domain`(`session`, `group`), `src/shared`, `src/mocks`(가짜 서버). 규칙은 `frontend/CLAUDE.md`, 근거는 `docs/리드미_프런트_구조_계획.md`.
- 데이터: 각 층의 `api.ts` 가 데이터를 읽고 고치는 한 곳이다. 지금은 `src/mocks/db.ts`(예시 데이터를 메모리에 둔다)를 부른다. 서버 응답은 각 `types.ts` 의 모양으로 온다고 보고 짰다.
- 아직 없는 것: 서버 호출(세션 쿠키, CSRF 헤더), 개발 프록시(`/api`, `/oauth2`, `/login/oauth2` → `localhost:8080`). 백엔드를 연결할 때 넣는다(`docs/리드미_배포_계획.md` 6절 4번).
- 동작이 없는 자리: 로그인 화면의 로그인·구글, 공고 올리기, 날짜 넣기, 그룹에서 내리기, 댓글 수정·삭제, 공개 범위 바꾸기, 헤더의 멤버·내 계정·내 그룹 모두 보기·그룹 만들기.
- `src/index.css`: Arc 변수(`src/components/arc/foundation.css`) 위에 리드미 값(색 A)을 덮고, shadcn/ui 색 이름을 Arc 변수에 연결한다.
- 부품: shadcn/ui 는 `src/components/ui`(button, input, textarea, dialog, dropdown-menu), dsui 는 `src/components/arc`(segmented-control, app-header 와 함께 들어온 기반). 헤더는 `src/app/layouts/group-header.tsx`(그룹 화면)와 `main-header.tsx`(그룹 밖 화면)를 따로 만들어 쓴다. 정한 값은 `reverse-engineering/리드미_디자인_적용_계획.md` 의 "적용 결과"에 있다.

## 확인한 것과 못 한 것 (2026-11-12, frontend 와 shadcn 줄은 2026-11-13)

| 대상 | 상태 |
|---|---|
| frontend | `npm run typecheck`, `npm run lint`, `npm run build` 통과. 그룹 공고 목록·상세를 1280px·390px, 라이트·다크로 띄워 `docs/design/shots` 와 비교했고 픽셀 차이는 0.1~0.4% |
| DB 마이그레이션 | (2026-11-13) Flyway 가 V1, V2 를 compose 의 PostgreSQL 16 과 Testcontainers 의 PostgreSQL 16 에 적용했다. V3 도 같은 날 Flyway 로 적용했다(`docs/리드미_설계_그룹_공고.md` 3.3절) |
| backend | (2026-11-13) Windows PC(JDK 21, Gradle 8.14.3)에서 `.\gradlew build` 통과. `bootRun` 으로 띄워 health `UP`, 로그인 전 401, CSRF 토큰 없는 요청 403, `localhost:5173` 만 받는 CORS, `XSRF-TOKEN` 쿠키 발급을 확인했다 |
| shadcn/ui, dsui | shadcn 프리셋(`base-nova`)으로 `frontend` 를 새로 만들고 부품을 받아 두었다. 위 frontend 줄의 빌드와 화면 비교에 포함된다 |
| GitHub Actions, deploy | 돌려 보지 못했다 |

## 다음에 할 일

1. 서버: 그룹·그룹 공고 API 의 나머지(올리기, 내리기, 날짜 넣기, 댓글 수정·삭제, 공개 범위, 그룹 만들기·나가기·초대, 내 지원 상태)를 만든다. 읽기와 관심·댓글 달기는 2026-11-13 에 만들었다(`docs/리드미_설계_그룹_공고.md`). 화면이 요구하는데 설계에 없는 것은 `docs/리드미_화면_API_대응.md` 3절에 있다.
2. GitHub Actions 가 도는지 확인한다. 저장소는 GitHub 에 올렸고(비공개), `gradlew` 의 실행 권한은 git 에 넣었다(`100755`).
3. 화면: 시안이 나온 25개 중 7개를 프런트에 옮겼다. 나머지를 옮긴다(`docs/리드미_화면_API_대응.md` 1절). 옮긴 화면에도 동작이 없는 자리가 남아 있다(위 "지금 들어 있는 것").
4. 로그인을 만든다(`docs/리드미_설계_로그인_경험카드.md` 4.1절). 임시 로그인은 이때 걷어낸다(`backend/CLAUDE.md`).

## 문서

아래 여덟 문서는 claude.ai 프로젝트 "리드미" 문서의 사본이다. 원본이 바뀌면 다시 복사한다.

- `docs/리드미_설계_로그인_경험카드.md` 테이블 10개, API 21개 (2026-11-13 사본)
- `docs/리드미_설계_그룹_공고.md` 그룹·그룹 공고의 테이블 9개, API 16개, 화면에 없는 로직 점검 (2026-11-13 사본)
- `docs/리드미_화면_API_대응.md` 화면 27개의 시안·프런트·서버 상태, 화면별로 쓰는 API, 시안이 요구하는데 설계에 없는 것 25개 (2026-11-13 사본)
- `docs/리드미_배포_계획.md` 구성, 비용, 뼈대에 미리 넣을 것 (2026-11-12 사본)
- `docs/리드미_리서치_재사용_정리.md` 정한 것과 남은 결정 (2026-11-13 사본)
- `docs/리드미_프런트_구조_계획.md` 프런트 폴더의 층, 옮길 파일, 지금 뽑는 공용 부품 (2026-11-13 사본)
- `reverse-engineering/리드미_디자인_적용_계획.md` shadcn/ui 와 dsui 설치 순서, 적용 결과 (2026-11-13 사본)
- `reverse-engineering/리드미_리서치_디자인_devs7udio.md` devs7ud.io 디자인 분석 (2026-11-12 사본)

`docs/design/` 은 사본이 아니라 이 폴더가 원본이다. 그룹 공고 목록·상세의 디자인 원본과 캡처가 있다. 나머지 화면의 시안은 캔버스에만 있다(주소는 `docs/리드미_화면_API_대응.md` 머리에 있다).
