# 리드미 저장소 작업 규칙

backend와 frontend에 함께 적용한다. frontend 코드 규칙은 `frontend/CLAUDE.md`에 따로 있다.

## 브랜치

- `main`: 배포할 수 있는 상태만 둔다. `dev`에서만 들어온다.
- `dev`: 작업을 모으는 곳이다. 작업 브랜치에서만 들어온다.
- 작업 브랜치: `dev`에서 따고 `dev`로 돌아간다. 이름은 `종류/영역-내용`으로 짓는다(예: `feat/backend-groups`, `fix/frontend-deadline`, `docs/sync-structure`). 종류는 커밋 머리말과 같은 `feat`, `fix`, `refactor`, `docs`, `test`, `build`, `chore`를 쓴다.

## 규칙

- `main`과 `dev`에는 직접 커밋하지 않는다. 모든 변경은 `dev`에서 딴 작업 브랜치에서 한다.
- 작업을 시작할 때: `git switch dev` → `git pull` → `git switch -c 종류/영역-내용`
- 작업 브랜치 → `dev`: 검사가 통과한 뒤 `git merge --no-ff`로 넣는다. 검사는 frontend가 typecheck·lint·test·build, backend가 `gradlew build`다. 사용자가 시킬 때만 한다.
- `dev` → `main`: 사용자가 시킬 때만 한다. `--no-ff`로 넣는다.
- 푸시: 작업 브랜치는 커밋 뒤 올려도 된다. `dev`와 `main`은 병합을 시켰을 때 함께 올린다.
- `main`과 `dev`에는 force push, rebase, reset을 하지 않는다. rebase는 아직 올리지 않은 작업 브랜치에서만 한다.
- `dev`에 들어간 작업 브랜치는 로컬과 원격에서 지운다.
- 한 작업 브랜치에는 한 가지 일만 담는다. frontend와 backend를 같이 고치는 일은 나눌 수 있으면 나눈다.
- 커밋 메시지는 영어 conventional commits로 쓴다(예: `feat(frontend): …`). 커밋과 PR에 Claude 표시(Co-Authored-By, Generated with, 세션 링크)를 넣지 않는다.
