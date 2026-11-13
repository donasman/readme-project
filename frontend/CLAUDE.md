# 리드미 프런트 작업 규칙

이 폴더(`frontend/`)의 코드를 만들거나 고칠 때 따른다. 근거와 옮기는 순서는 `../docs/리드미_프런트_구조_계획.md`에 있다.

- 계획 문서 9절 중 정한 것(2026-11-13): 이름은 계획 그대로, 가짜 서버는 `mocks/`로 분리, 글자 규칙은 뿌리 틀로 올림, `/dev/components`는 개발 때만 등록. ESLint 경계 규칙과 테스트는 아직 정하지 않았다. 8단계에 오면 사용자에게 묻는다. 이름이 바뀌면 이 파일도 같이 고친다.

## 층

위에서 아래로만 불러온다.

```
src/app → src/features/<기능> → src/domain/<이름> → src/shared, src/components
```

- `app/`: 라우터, 틀(헤더), 오류 화면, 시작 설정. 기능은 `features/<기능>/index.ts`로만 불러온다.
- `features/<기능>/`: 화면 단위 묶음. 다른 기능과 `app/`을 불러오지 않는다. 기능 안에서는 상대 경로로 불러온다.
- `domain/<이름>/`: 두 기능 이상이 쓰는 개념(`session`, `group`). `features/`와 `app/`을 불러오지 않는다.
- `shared/`: 도메인을 모르는 코드. `domain/`, `features/`, `app/`을 불러오지 않는다.
- `components/ui`(shadcn), `components/arc`(dsui): 받아 온 부품이다. 폴더를 옮기지 않는다. `arc`는 고치지 않는다.
- `mocks/`: 가짜 서버. 각 층의 `api.ts`만 부른다. `mocks/`는 `shared/`와 타입(`import type`)만 불러온다.

## 새 코드를 둘 자리

- 새 화면: `features/<기능>/pages/`에 만들고 그 기능의 `routes.tsx`에 `lazy`로 등록한다. `app/router.tsx`는 기능의 라우트 묶음을 조립만 한다.
- 서버와 주고받는 것: 그 층의 `api.ts` 한 곳에 둔다. 응답 모양은 `types.ts`에 둔다. 화면과 훅은 `api.ts`를 거친다.
- 화면과 무관한 규칙(필터, 정렬, 날짜 계산): 순수 함수 파일에 둔다(`list-rules.ts`, `deadline.ts`처럼). 컴포넌트 안에 두지 않는다.
- 주소: `shared/lib/paths.ts`의 함수로 만든다. 문자열로 직접 만들지 않는다.
- 아이콘: `shared/icons.ts`에서만 lucide를 불러온다.
- 보는 사람과 그룹: `useViewer()`, `useGroup()`으로 읽는다. 기능의 loader 응답에 다시 싣지 않는다.
- 지금 메뉴 표시: 라우트의 `handle.section`으로 정한다. 틀에 고정값을 넣지 않는다.

## 올리고 뽑는 기준

- 같은 모양이 3곳에서 나오면 공용 부품으로 뽑는다. 2곳까지는 그대로 둔다.
- 2곳이라 기다리는 모양은 계획 문서 6절에 있다. 세 번째를 만들게 되면 그때 뽑고 6절에서 지운다. 새로 2곳이 된 모양은 6절에 더한다.
- 뽑은 부품이 갈 자리: 도메인을 모르면 `shared/components/`, 도메인 타입을 받으면 `domain/<이름>/`, 한 기능만 쓰면 그 기능의 `components/`.
- 공용 부품(`Page`, `SurfaceCard`, `Heading`, `Avatar`, `textLink`)이 있는 모양은 클래스 줄로 다시 적지 않는다.
- 같은 함수나 계산이 두 번 나오면 바로 하나로 합친다. 모양과 무관한 로직은 기다리지 않는다.
- 두 번째 기능이 어떤 타입이나 api를 필요로 하면 `domain/`으로 올린다. 기능끼리 불러오게 하지 않는다.
- 공용 부품을 뽑거나 고칠 때는 값(여백, 색, 모서리)을 바꾸지 않는다. 화면 모양이 달라지면 안 된다.

## 확인

- 커밋 전에 `npm run typecheck`, `npm run lint`, `npm run build`를 돌린다.
- 층 규칙을 어긴 import가 없는지 본다: `shared/`에서 `@/domain`, `@/features`, `@/app`을, `domain/`에서 `@/features`, `@/app`을, `features/`에서 `@/app`과 다른 기능을 찾는다.
