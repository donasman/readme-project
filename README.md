# 리드미

취업준비생을 위한 정보공유·스터디 웹 서비스. 서버와 화면을 한 저장소에서 관리한다.

```
readme/
├─ backend/            Spring Boot 4.1.2 · Java 21 · Gradle        → 배포: AWS(초안)
├─ frontend/           Vite · React · TypeScript · Tailwind CSS    → 배포: Cloudflare Pages
├─ docs/               설계 초안, 배포 계획, 조사 정리, 화면 디자인(design)
├─ reverse-engineering/ devs7ud.io 디자인 분석, 디자인 적용 계획
├─ deploy/             서버에 올릴 때 쓰는 파일 초안(Caddy, systemd), GitHub Actions 파일(github-workflows)
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
# 1) DB 와 메일 수신기
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

- `http://localhost:8080/actuator/health` 가 `{"status":"UP"}` 을 돌려준다. 서버가 뜰 때 Flyway 가 테이블 12개를 만들므로, 서버가 떴다면 DB 연결과 테이블 생성까지 된 것이다.
- `http://localhost:5173` 은 내 첫 그룹의 공고 목록(`/groups/be/posts`)으로 넘어간다. 화면은 아직 서버를 부르지 않고 예시 데이터로 돈다. 서버가 꺼져 있어도 화면은 뜬다.
- 보낸 메일은 `http://localhost:8025` 에서 본다(아직 메일을 보내는 기능은 없다).

## 빌드와 테스트

```powershell
cd backend;  .\gradlew build      # 테스트 포함. Docker 가 켜져 있어야 한다(Testcontainers). 테스트 없이: .\gradlew build -x test
cd frontend; npm run lint; npm run build
```

## 지금 들어 있는 것

**backend** (`com.readme`)

- 설정: 세션 쿠키 로그인의 바탕(Spring Session JDBC), CORS, CSRF, 로그인 전 요청에 401, `/actuator/health`.
- DB: `V1__init.sql`(설계 초안의 테이블 10개), `V2__spring_session.sql`(세션 테이블 2개). 스키마는 `readme`.
- 프로필: `dev`(기본, compose.yaml 의 DB), `prod`(환경 변수, `backend/.env.example`).
- 패키지: `auth`, `card`, `extraction`, `keyword`, `llm`, `common`. 설명만 있고 기능 코드는 아직 없다.
- 자원: `resources/dictionary`(키워드 사전 4개), `resources/prompts/exp-extract.md`(jobfit 원본 그대로. 옮길 때 첫 줄의 jobfit 안내와 변수 표기를 고친다).

**frontend** (2026-11-13)

- 화면 2개: 그룹 공고 목록 `/groups/:groupId/posts`, 그룹 공고 상세 `/groups/:groupId/posts/:postId`. `/` 는 내 첫 그룹의 목록으로 보낸다. `/dev/components` 는 부품 확인용이다.
- 화면의 기준은 `docs/design/`(두 화면의 디자인 원본과 캡처)이다.
- `src/features/group-posts/api.ts`: 데이터를 읽고 고치는 한 곳. 지금은 예시 데이터(`example-data.ts`)를 메모리에 둔다. 서버 응답은 `types.ts` 의 모양으로 온다고 보고 짰다.
- 아직 없는 것: 서버 호출(세션 쿠키, CSRF 헤더), 개발 프록시(`/api`, `/oauth2`, `/login/oauth2` → `localhost:8080`), 로그인·경험카드 화면. 백엔드를 연결할 때 넣는다(`docs/리드미_배포_계획.md` 6절 4번).
- 동작이 없는 자리: 공고 올리기, 날짜 넣기, 그룹에서 내리기, 댓글 수정·삭제, 공개 범위 바꾸기, 헤더의 멤버·내 계정·내 그룹 모두 보기·그룹 만들기.
- `src/index.css`: Arc 변수(`src/components/arc/foundation.css`) 위에 리드미 값(색 A)을 덮고, shadcn/ui 색 이름을 Arc 변수에 연결한다.
- 부품: shadcn/ui 는 `src/components/ui`(button, input, textarea, dialog, dropdown-menu), dsui 는 `src/components/arc`(segmented-control, app-header 와 함께 들어온 기반). 헤더는 `src/components/layout/group-header.tsx` 를 따로 만들어 쓴다. 정한 값은 `reverse-engineering/리드미_디자인_적용_계획.md` 의 "적용 결과"에 있다.

## 확인한 것과 못 한 것 (2026-11-12, frontend 와 shadcn 줄은 2026-11-13)

| 대상 | 상태 |
|---|---|
| frontend | `npm run typecheck`, `npm run lint`, `npm run build` 통과. 그룹 공고 목록·상세를 1280px·390px, 라이트·다크로 띄워 `docs/design/shots` 와 비교했고 픽셀 차이는 0.1~0.4% |
| DB 마이그레이션 | V1, V2 를 PostgreSQL 16 에 직접 적용해 확인. Flyway 를 거친 실행은 확인 못 함 |
| backend | **컴파일과 실행을 확인하지 못했다.** 뼈대를 만든 환경에서 Maven Central 에 접속할 수 없었다. Java 문법 검사와, 의존성 이름·설정 이름을 Spring Boot 4.1.2 소스와 대조하는 것까지 했다. PC 에서 `.\gradlew build` 로 확인이 필요하다 |
| shadcn/ui, dsui | shadcn 프리셋(`base-nova`)으로 `frontend` 를 새로 만들고 부품을 받아 두었다. 위 frontend 줄의 빌드와 화면 비교에 포함된다 |
| GitHub Actions, deploy | 돌려 보지 못했다 |

## 다음에 할 일

1. `backend` 에서 `.\gradlew build` 를 돌려 서버 빌드를 확인한다.
2. 저장소를 GitHub 에 올린다. 로컬 저장소는 만들었고 원격은 아직 없다. `gradlew` 의 실행 권한이 빠져 있으므로(`100644`) `git update-index --chmod=+x backend/gradlew` 를 한 번 해 둔다.
   - 빌드 확인(CI)을 켜려면 `deploy/github-workflows/` 의 `backend.yml`, `frontend.yml` 을 `.github/workflows/` 로 옮긴다. 원격 작업으로는 `.github/workflows/` 에 파일을 쓸 수 없어서 따로 두었다.
3. 다음 화면을 정한다. 그룹 공고 화면 2개는 만들었고, 동작이 없는 자리가 남아 있다(위 "지금 들어 있는 것").
4. 로그인을 만든다(`docs/리드미_설계_로그인_경험카드.md` 4.1절). 그룹과 그룹 공고의 테이블·API는 `docs/리드미_설계_그룹_공고.md` 에 초안이 있다.

## 문서

아래 일곱 문서는 claude.ai 프로젝트 "리드미" 문서의 사본이다. 원본이 바뀌면 다시 복사한다.

- `docs/리드미_설계_로그인_경험카드.md` 테이블 10개, API 21개 (2026-11-12 사본)
- `docs/리드미_설계_그룹_공고.md` 그룹·그룹 공고의 테이블 9개, API 16개, 화면에 없는 로직 점검 (2026-11-13 사본)
- `docs/리드미_배포_계획.md` 구성, 비용, 뼈대에 미리 넣을 것 (2026-11-12 사본)
- `docs/리드미_리서치_재사용_정리.md` 정한 것과 남은 결정 (2026-11-13 사본)
- `docs/리드미_프런트_구조_계획.md` 프런트 폴더의 층, 옮길 파일, 지금 뽑는 공용 부품 (2026-11-13 사본)
- `reverse-engineering/리드미_디자인_적용_계획.md` shadcn/ui 와 dsui 설치 순서, 적용 결과 (2026-11-13 사본)
- `reverse-engineering/리드미_리서치_디자인_devs7udio.md` devs7ud.io 디자인 분석 (2026-11-12 사본)

`docs/design/` 은 사본이 아니라 이 폴더가 원본이다. 그룹 공고 목록·상세의 디자인 원본과 캡처가 있다.
