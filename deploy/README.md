# deploy

서버에 올릴 때 쓰는 파일의 초안이다. 서버 구성(Lightsail + Caddy + systemd)은 아직 확정 전이고, 도메인도 정해지지 않았다.

| 파일 | 쓰는 곳 |
|---|---|
| `Caddyfile` | 서버의 `/etc/caddy/Caddyfile`. HTTPS 를 받아 Spring Boot(8080)로 넘긴다 |
| `readme-api.service` | 서버의 `/etc/systemd/system/readme-api.service`. Spring Boot 를 서비스로 띄운다 |
| `github-workflows/backend.yml`, `frontend.yml` | 저장소의 `.github/workflows/`. 폴더가 바뀔 때 빌드를 확인한다. 이 위치로 옮겨야 동작한다 |

- JAR 는 `backend` 에서 `gradlew bootJar` 로 만들고(`build/libs/readme-backend-<버전>.jar`), 서버의 `/opt/readme/readme-backend.jar` 로 복사한다.
- 환경 변수 목록은 `backend/.env.example` 에 있다.
- 전체 계획과 비용은 `docs/리드미_배포_계획.md` 에 있다.

네 파일 모두 실제로 돌려 본 적은 없다.
