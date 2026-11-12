# 리드미

취업준비생을 위한 정보공유·스터디 웹 서비스. 서버와 화면을 한 저장소에서 관리한다.

```
readme/
├─ backend/            Spring Boot 4.1.2 · Java 21 · Gradle        → 배포: AWS(초안)
├─ frontend/           Vite · React · TypeScript · Tailwind CSS    → 배포: Cloudflare Pages
├─ docs/               설계 초안, 배포 계획, 조사 정리
├─ deploy/             서버에 올릴 때 쓰는 파일 초안(Caddy, systemd), GitHub Actions 파일(github-workflows)
└─ compose.yaml        개발용 PostgreSQL 16 + 메일 수신기(Mailpit)
```

## 필요한 것

| 도구 | 버전 | 쓰는 곳 |
|---|---|---|
| JDK | 21 | 서버 빌드·실행. 다른 버전만 있으면 Gradle 이 21을 찾지 못해 멈춘다 |
| Node.js | 22.12 이상(또는 20.19 이상) | 화면 빌드·실행 |
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
- `http://localhost:5173` 오른쪽 위에 **서버 연결됨 · 로그인 전** 이 보인다. 화면 → Vite 프록시 → 서버까지 이어진 것이다. **서버에 연결할 수 없음** 이면 서버가 꺼져 있다.
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

**frontend**

- 화면 8개의 주소와 자리 표시: `/login`, `/signup`, `/verify-email`, `/reset-password`, `/cards`, `/cards/new`, `/cards/:cardId`, `/cards/import`, `/cards/review/:jobId`.
- `src/lib/api.ts`: 서버 호출 한 곳. 세션 쿠키를 함께 보내고 CSRF 헤더를 붙인다.
- `src/index.css`: 색·모서리 변수. 디자인 시안 A 의 값이고, 이름은 shadcn/ui 변수 이름이다.
- 개발 프록시: `/api`, `/oauth2`, `/login/oauth2` → `localhost:8080`.

## 확인한 것과 못 한 것 (2026-11-12)

| 대상 | 상태 |
|---|---|
| frontend | `npm run build`, `npm run lint` 통과. 화면 4개가 오류 없이 뜨는 것을 브라우저로 확인 |
| DB 마이그레이션 | V1, V2 를 PostgreSQL 16 에 직접 적용해 확인. Flyway 를 거친 실행은 확인 못 함 |
| backend | **컴파일과 실행을 확인하지 못했다.** 뼈대를 만든 환경에서 Maven Central 에 접속할 수 없었다. Java 문법 검사와, 의존성 이름·설정 이름을 Spring Boot 4.1.2 소스와 대조하는 것까지 했다. PC 에서 `.\gradlew build` 로 확인이 필요하다 |
| shadcn/ui | `components.json` 과 스타일을 shadcn 수동 설치 문서대로 넣었다. `npx shadcn@latest add …` 실행은 확인 못 함 |
| GitHub Actions, deploy | 돌려 보지 못했다 |

## 다음에 할 일

1. `backend` 에서 `.\gradlew build` 를 돌려 서버 빌드를 확인한다.
2. 저장소를 만든다: `git init` → 첫 커밋 → GitHub 에 올린다. Windows 에서 커밋하면 `gradlew` 의 실행 권한이 빠지므로 `git update-index --chmod=+x backend/gradlew` 를 한 번 해 둔다.
   - 빌드 확인(CI)을 켜려면 `deploy/github-workflows/` 의 `backend.yml`, `frontend.yml` 을 `.github/workflows/` 로 옮긴다. 원격 작업으로는 `.github/workflows/` 에 파일을 쓸 수 없어서 따로 두었다.
3. 화면 부품을 넣는다. `reverse-engineering/리드미_디자인_적용_계획.md` 4절의 2단계부터 이어서 한다(1단계 "프로젝트 만들기"는 이 뼈대의 `frontend` 가 대신한다). 예: `cd frontend` → `npx shadcn@latest add input select dialog`. 스타일 묶음은 `components.json` 의 `style`(`base-nova`)이고, 부품을 넣기 전에 바꿀 수 있다.
4. 로그인부터 만든다(`docs/리드미_설계_로그인_경험카드.md` 4.1절).

## 문서

`docs/` 의 세 문서는 claude.ai 프로젝트 "리드미" 문서의 2026-11-12 사본이다. 원본이 바뀌면 다시 복사한다.

- `docs/리드미_설계_로그인_경험카드.md` 테이블 10개, API 21개
- `docs/리드미_배포_계획.md` 구성, 비용, 뼈대에 미리 넣은 것
- `docs/리드미_리서치_재사용_정리.md` 정한 것과 남은 결정
