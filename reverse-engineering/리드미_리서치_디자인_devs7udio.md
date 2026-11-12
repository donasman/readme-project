# 리드미 리서치 — devs7ud.io 디자인 분석

2026-11-12 기준. https://devs7ud.io/ 홈 화면 한 장을 Chrome에서 열어 실제 적용된 값을 읽었다. 픽셀 수치는 1424px 창과 390px 폭에서 잰 것이다. 색의 hex는 사이트가 내려 주는 lab() 값을 sRGB로 바꾼 값이다.

## 1. 요약

- 이 사이트는 공개된 디자인 시스템으로 만들어져 있다. 바탕은 Arc(uiarc.dev)이고, devs7udio가 자기 항목을 더해 dsui(dsui.vercel.app)라는 shadcn 레지스트리로 배포한다. `npx shadcn@latest add`로 설치하는 방식이다.
- 디자인 값은 CSS 변수에 모여 있다. `:root`에 142개, 다크 모드용으로 70개가 있고 컴포넌트는 이 변수만 읽는다.
- 화면 인상을 만드는 요소는 다섯 가지다.
  1. 유채색은 파랑 `#2563eb`와 노랑 `#fbbf24` 두 개뿐이다. 나머지는 푸른 기가 도는 회색이다.
  2. 모서리가 크다. 18, 26, 34px 세 단계와 알약 모양만 쓴다.
  3. 테두리보다 면 색으로 구분한다. 그림자는 옅고, 색이 있는 면에는 같은 색 그림자를 준다.
  4. 제목은 Pretendard 800~900 굵기에 자간을 좁힌다.
  5. 올리면 커지고(1.04배) 누르면 줄어드는(0.94배) 반응이 거의 모든 요소에 있다.

## 2. 구성

| 항목 | 내용 | 근거 |
|---|---|---|
| 프레임워크 | Next.js | 정적 파일 경로가 `/_next/static/` |
| 스타일 방식 | CSS Modules. 컴포넌트가 CSS 변수를 읽는다 | 클래스명이 `해시_이름` 꼴(`Bsbd8q_rank`) |
| Tailwind | v4가 함께 들어 있으나 컴포넌트 스타일에는 거의 안 쓰인다 | `--tw-*`, `--spacing: .25rem` 변수 |
| 색 표기 | 빌드 때 lab()으로 바뀌어 내려온다 | `--lightningcss-light`, `--lightningcss-dark` 변수 |
| 글꼴 | Pretendard Variable 한 파일(굵기 45~930)을 자체 서버에서 받는다. Geist, Geist Mono도 로드된다 | `@font-face`, `/fonts/PretendardVariable.woff2` |
| 다크 모드 | `<html data-theme="dark">` 또는 `class="dark"`. 둘 다 없으면 OS 설정을 따른다 | 변수 선택자 |
| 디자인 시스템 | Arc 기반 + dsui 레지스트리 | 푸터의 "Powered by Arc", dsui 링크, dsui 설치 문서 |
| 아이콘, 모션 | Hugeicons, motion 패키지 | dsui 설치 문서 |

## 3. 토큰

### 3.1 색

| 역할 | 변수 | 라이트 | 다크 |
|---|---|---|---|
| 페이지 바탕 | `--background` | #ffffff | #101214 |
| 카드 면 | `--surface` | #ffffff | #101214 |
| 떠 있는 면 | `--surface-raised` | #ffffff | #1e2226 |
| 옅은 면 | `--surface-muted` | #f5f7fa | #171a1d |
| 눌린 면 | `--surface-sunken` | #e7ebf0 | #0b0d0f |
| 본문 글자 | `--foreground` | #252729 | #f5f5f5 |
| 보조 글자 | `--text-secondary`, `--text-muted` | #6b7682 | #9aa5b1 |
| 테두리 | `--border` | #dfe5eb | #2a2f35 |
| 옅은 테두리 | `--border-subtle` | #ecf1f5 | #1f2328 |
| 진한 테두리 | `--border-strong` | #b7bfc7 | #474e55 |
| 강조 | `--accent` | #2563eb | #6c9bff |
| 강조 위 글자 | `--accent-foreground` | #ffffff | #0b1220 |
| 옅은 강조 면 | `--accent-subtle` | 강조색 12% | 강조색 18% |
| 하이라이트 | `--highlight` | #fbbf24 | #fbbf26 |
| 하이라이트 위 글자 | `--highlight-foreground` | #252729 | #252729 |
| 반전 면과 글자 | `--inverse`, `--inverse-foreground` | #252729, #ffffff | #252729, #f5f5f5 |
| 성공 | `--success` | #37b66c | #59d989 |
| 경고 | `--warning` | #b66400 | #ffb90e |
| 위험 | `--danger` | #e35462 | #ff736d |
| 스위치 트랙 | `--control-track` | #d9dde0 | #323639 |
| 차트 1~4 | `--series-1`~`4` | #2563eb, #fbbf24, #9623f1, #03cf4d | #6c9bff, #fbbf26, #d961d2, #19cdac |

- 강조색은 Tailwind blue-600, 하이라이트는 amber-400과 같은 값이다.
- `--accent-strong`과 `--accent-pressed`는 `--accent`와 같은 값이다. `--text-muted`도 `--text-secondary`와 같다.
- 무채색 12단계(`--neutral-0`~`11`, #ffffff~#0b0b0b)가 따로 정의돼 있지만 면과 테두리는 위 표의 푸른 회색을 쓴다.
- 홈에서 글자색이 쓰인 횟수: #6b7682 83곳, #252729 72곳, #ffffff 28곳.

### 3.2 글자

글꼴은 Pretendard Variable 하나다. 본문 전체에 자간 -0.01em이 걸려 있다.

크기 변수: 12, 14, 16, 18, 22, 28, 36, 52, 72px (`--text-xs`~`5xl`). 버튼·탭용 라벨은 13, 14, 16px에 굵기 700이다.

홈에서 실제로 쓰인 조합:

| 쓰임 | 크기 (모바일) | 줄높이 | 굵기 | 자간 |
|---|---|---|---|---|
| 색 밴드 헤드라인 | 76px (32px) | 1.04 | 900 | -0.048em |
| 페이지 제목 | 64px (40px) | 1.1 | 800 | -0.02em |
| 대표 카드 제목 | 34px (28px) | 1.2 | 800 | -0.015em |
| 섹션 제목 | 32px | 1.2 | 800 | -0.01em |
| 카드 제목 | 28px (22px) | 1.25 | 800 | -0.01em |
| 목록 항목 제목 | 21px (16px) | 1.32 | 800 | -0.01em |
| 작은 카드 제목 | 19px (16px) | 1.3 | 700 | 본문과 같음 |
| 본문 | 16px | 1.4~1.5 | 400 | -0.01em |
| 항목 이름 | 16px | 1.5 | 600~700 | -0.01em |
| 보조 설명 | 14px | 1.4~1.5 | 400 | -0.01em |
| 버튼, 탭 | 14px | 1.4~1.5 | 700 | -0.01em |
| 알약 버튼("보기") | 14px | 1.5 | 600 | -0.01em |
| 메타, 배지 | 12px | 1.5~1.7 | 600 | -0.01em |

제목 크기는 대부분 `clamp()`로 창 폭에 따라 변한다. 페이지 제목은 `clamp(40px, 7vw, 64px)`, 밴드 헤드라인은 `clamp(32px, 5.6vw, 76px)`다. 제목에는 `text-wrap: balance`와 `word-break: keep-all`, 2~3줄 말줄임이 걸려 있다.

### 3.3 간격

4의 배수 12단계다: 4, 8, 12, 16, 20, 24, 32, 40, 48, 64, 80, 96px (`--space-1`~`24`).

홈에서 쓰인 gap: 4px 37곳, 8px 33곳, 12px 10곳, 24px 7곳, 그 밖에 16, 20, 48px. 탭과 내비 안쪽에는 2~3px도 쓴다.

### 3.4 모서리

| 변수 | 값 | 쓰이는 곳 | 홈 사용 횟수 |
|---|---|---|---|
| `--radius-control` | 18px | 아이콘 타일, 썸네일, 건너뛰기 링크 | 16 |
| `--radius-panel` | 26px | 카드 안 그림, 떠 있는 패널 | 9 |
| `--radius-surface` | 34px | 카드 | 12 |
| `--radius-pill` | 9999px | 버튼, 탭, 배지, 헤더 바 | 89 |

원형(50%)은 75곳에서 쓰인다. dsui 테마 문서는 세 단계 값만 바꾸고 알약은 고정하라고 적는다.

### 3.5 그림자

| 변수 | 라이트 | 다크 |
|---|---|---|
| `--shadow-resting` | 0 1px 2px 검정 3.5% | 0 1px 2px 검정 18% |
| `--shadow-raised` | 0 6px 18px 검정 6.5% + 0 1px 3px 검정 3.5% | 0 8px 22px 검정 22% |
| `--shadow-floating` | 0 20px 48px 검정 10.5% + 0 3px 10px 검정 4.5% | 0 22px 55px 검정 34% |
| 색 그림자 resting | 0 4px 12px 해당 색 30% | 같음 |
| 색 그림자 raised | 0 8px 20px 해당 색 36% + 0 2px 6px 해당 색 20% | 같음 |
| 색 그림자 floating | 0 18px 44px 해당 색 36% + 0 4px 12px 해당 색 20% | 같음 |

색 그림자는 accent, highlight, danger, success, warning 다섯 색에 각각 있다. 파란 버튼과 노란 카드가 자기 색 그림자를 쓴다. 홈에서는 파랑 resting 7곳, 무채색 resting 7곳, 노랑 resting 4곳이다.

### 3.6 컨트롤 높이

36, 44, 50px (`--control-height-sm`, `md`, `lg`).

### 3.7 모션

| 변수 | 값 |
|---|---|
| `--duration-micro` ~ `spring` | 80, 120, 160, 240, 320, 480, 580ms |
| `--ease-enter` | cubic-bezier(.16, 1, .3, 1) |
| `--ease-standard` | cubic-bezier(.22, 1, .36, 1) |
| `--ease-exit` | cubic-bezier(.7, 0, .84, 0) |
| `--ease-in-out` | cubic-bezier(.65, 0, .35, 1) |
| `--ease-spring` | `linear()` 함수로 만든 스프링 곡선. 끝에서 0.6% 넘쳤다가 돌아온다 |

쓰는 규칙: 색·배경·테두리는 160ms standard, 크기 변화(scale)는 580ms spring, 누르는 순간은 120ms, 너비·높이 변화는 240ms standard, 헤더 숨김은 480ms enter.

### 3.8 중단점

480, 640, 768, 1024, 1280px (30, 40, 48, 64, 80rem). 홈과 헤더는 이 다섯 개로 움직인다. 카드 안쪽 배치는 컨테이너 쿼리로도 바뀐다(규칙 13개).

## 4. 레이아웃

| 항목 | 데스크톱 (1424px) | 모바일 (390px) |
|---|---|---|
| 홈 본문 폭 | 최대 1240px, 좌우 16px | 좌우 16px |
| 본문 위아래 여백 | 위 64px, 아래 48px | 위 40px, 아래 48px |
| 섹션 사이 | 48px | 48px |
| 상단 카드 줄 | 387px 3열, 간격 24px | 1열, 간격 20px |
| 대표 카드 | 387 × 553px | 343 × 576px |
| 작은 카드 격자 | 290px 4열, 간격 16px, 비율 3:4 | 2열, 간격 12px |
| 뉴스 목록 | 584px 2열, 열 간격 40px | 1열 |
| 색 밴드 위아래 여백 | 157px (`clamp(80px, 11vw, 168px)`) | 80px |
| 일반 페이지 폭 | 넓게 72rem(1152px), 좁게 46rem(736px) | |
| 푸터 | 최대 1152px, 소개 0.8 : 링크 1.6 두 칸, 간격 48px | 1열, 간격 40px |

홈 순서: 날짜와 페이지 제목, 분류 탭, 상단 카드 3장(대표 카드 1장은 5초마다 넘어가는 슬라이드), 뉴스 목록, 트렌드 카드 4장, 딥다이브 카드 4장, 파란 구독 카드, 노란 문의 밴드, 푸터.

## 5. 컴포넌트

### 5.1 헤더

768px 이상에서는 가운데에 떠 있는 알약 바다.

- 바: 높이 68px, 안쪽 여백 좌우 8px, 항목 간격 4px, 너비는 내용만큼. 배경은 흰색 78%에 `blur(24px) saturate(1.8)`, 테두리 1px.
- 항목: 52px 높이 알약. 현재 페이지는 먹색(`--foreground`) 알약에 흰 글자, 굵기 700.
- 1280px 미만에서는 항목이 아이콘만 남는다. 글자는 `grid-template-columns: 0fr`에서 `1fr`로 펼쳐진다.
- 스크롤하면 바 높이 60px, 항목 44px로 줄고 `--shadow-floating`이 붙는다. 내려갈 때 숨고 올라올 때 나타난다.
- 오른쪽 끝 버튼: 52px 원, 파랑 배경, 파랑 그림자.
- 하위 메뉴: 최소 폭 200px, 모서리 26px, 안쪽 8px, 항목 높이 44px 알약. 나타날 때 0.96배에서 1배로 커진다.

### 5.2 모바일 내비

768px 미만에서는 위에 56px 원 두 개(왼쪽 로고, 오른쪽 메뉴)만 남고, 아래에 독이 뜬다.

- 독: 252 × 70px 알약, 화면 아래 가운데, 흰색 78%, `--shadow-floating`. 항목은 오늘, 읽기, 검색 세 개다.
- 독에 가리지 않게 본문 아래에 100px 여백을 둔다.

### 5.3 분류 탭 (세그먼트)

- 트랙: `--surface-muted` 알약, 안쪽 3px, 탭 간격 2px. 전체 높이 46px.
- 탭: 높이 40px, 최소 폭 40px, 좌우 12px(선택 시 16px), 14px 굵기 700, 글자색 보조.
- 선택 표시: 흰 면 + 테두리 1px + `--shadow-resting`인 알약이 탭 뒤에서 이동한다.
- 선택 안 된 탭에 올리면 배경 `--control-track-hover`(#cdd1d5). 누르면 0.94배.

### 5.4 버튼

| 구분 | 값 |
|---|---|
| 모양 | 알약, 테두리 1px(기본 투명) |
| 크기 | sm 36px · 좌우 16px · 13px / md 44px · 좌우 20px · 14px / lg 50px · 좌우 24px · 16px |
| 글자 | 굵기 700, 줄높이 1.4, 아이콘과 간격 8px |
| primary | 파랑 배경, 흰 글자, 파랑 그림자. 올리면 불투명도 0.91에 그림자 한 단계 위 |
| secondary | 흰 면, `--border` 테두리. 올리면 `--surface-muted` |
| ghost | 배경 없음, 보조 글자색. 올리면 `--accent-subtle` 배경 |
| accent | 노랑 배경, 먹색 글자, 노랑 그림자 |
| danger | 흰 면, 빨강 글자. 올리면 빨강 테두리 |
| 비활성 | `--border` 배경, 본문색 55% 글자, 그림자 없음 |

### 5.5 배지와 작은 알약

- 분류 배지: 알약, 배경은 글자색의 12%, 12px 굵기 600, 안쪽 `1px 8px 1px 6px`, 아이콘 12px과 간격 4px.
- 강조 배지("월간"): 파랑 배경, 흰 글자, 안쪽 `1px 10px`, 파랑 그림자.
- 알약 버튼("읽기"): 흰 면, 14px 굵기 600, 안쪽 `4px 16px`, `--shadow-resting`. 조용한 변형("보기")은 `--surface-muted` 배경에 그림자 없음.

### 5.6 카드

| 카드 | 면 | 안쪽 여백 | 그 밖 |
|---|---|---|---|
| 대표 카드 | 노랑, 모서리 34px, 노랑 그림자 | 그림 둘레 16px, 글 좌우 24px | 그림 모서리 26px. 아래 띠는 먹색 12% 배경에 안쪽 `16px 24px` |
| 랭킹 카드 | 흰 면, 테두리 1px, 모서리 34px, `--shadow-resting` | `24px 24px 16px` | 제목 28px 2줄 말줄임. 오른쪽에 104px 그림 |
| 작은 톤 카드 | 톤 4종, 모서리 34px | 16px | 비율 3:4. 위에 그림(모서리 26px), 아래에 분류 12px과 제목 19px |
| 이야기 카드 | 톤 4종, 모서리 34px | 24px | 최소 높이 240px, 그림 16:9 |
| 뉴스 카드 | `--surface-muted`, 모서리 34px | `16px 20px` | 썸네일 136px 3:2 모서리 18px. 올리면 `--shadow-raised` |

톤 4종: yellow(노랑 면 + 노랑 그림자), blue(파랑 면 + 파랑 그림자), ink(먹색 면, 흰 글자), sunk(`--surface-sunken` 면). 홈의 트렌드 줄은 노랑·파랑을 번갈아, 딥다이브 줄은 sunk·ink·노랑·파랑 순으로 쓴다.

카드에 올리면 1.01~1.02배, 누르면 0.97~0.985배다.

### 5.7 랭킹 행

- 행: 위아래 12px, 항목 간격 12px. 행 사이 구분선은 1px `--border-subtle`이고 왼쪽 72px을 비운다.
- 순위 숫자 16px 굵기 700, 아이콘 타일 52px(모서리 18px, `--surface-sunken`), 이름 16px 굵기 600, 수치 14px 보조색.
- 점수 막대: 높이 8px 알약, 투명에서 파랑으로 가는 그라디언트. 1위만 노랑이다.

### 5.8 타임라인 목록 (뉴스)

- 왼쪽에 2px 세로선과 16px 점(흰 테두리 3px). 첫 항목의 점은 노랑에 노랑 고리 두 겹(1px 60%, 5px 30%)이다.
- 항목: 글 영역과 정사각 썸네일(160px, 좁으면 84px, 모서리 18px) 두 칸. 글 영역은 메타 12px, 제목 21px 굵기 800 3줄, 요약 14px 2줄.
- 올리면 제목에 2px 밑줄이 생기고 썸네일이 1.04배가 된다.

### 5.9 색 밴드

- 화면 폭 전체를 채우는 구역이다. 톤은 plain, blue, yellow, ink 네 가지이고 배경·글자·보조 글자색을 한꺼번에 바꾼다.
- 헤드라인은 단어가 아래에서 올라오며 나타난다. 앞줄은 보조색, 핵심 줄은 본문색이다.
- 본문은 `clamp(16px, 1.7vw, 21px)`, 굵기 500, 줄높이 1.65, 최대 44자 폭.
- 오른쪽 배경에 3~10px 점들이 흩어져 있고 왼쪽으로 갈수록 사라진다.

### 5.10 그 밖

- 구독 카드: 파랑 면, 흰 알약 버튼, 오른쪽 위에 원·알약·고리 도형.
- 푸터: 링크 14px 보조색, 열 제목 14px 굵기 500, SNS 아이콘은 36px 원 버튼.
- 화면 구석 버튼: 44px 원. 왼쪽 아래 쿠키 설정, 오른쪽 아래 맨 위·맨 아래 이동.

## 6. 상호작용 규칙

- 올림 효과는 `@media (hover: hover) and (pointer: fine)` 안에만 둔다(규칙 168개). 터치 기기에서는 올림 효과가 없다.
- 크기 반응: 작은 요소는 올리면 1.04배, 누르면 0.94배. 카드는 1.01~1.02배, 0.97~0.985배.
- 포커스: 외곽선 2px, 색은 강조색 72%. 색 밴드 안에서는 그 밴드의 글자색을 쓴다.
- 동작 줄이기: `prefers-reduced-motion` 규칙 174개. `<html data-motion="reduce">`, `data-blur="reduce"` 속성으로도 끈다.
- 겹침 순서: 구석 버튼 28, 헤더 40, 떠 있는 층 45, 하위 메뉴 60.

## 7. shadcn/ui 변수와의 대응

shadcn/ui 테마 변수에 이 사이트 값을 넣는다면 다음과 같이 대응한다.

| shadcn/ui 변수 | 이 사이트의 변수 | 라이트 값 |
|---|---|---|
| `--background`, `--foreground` | 같은 이름 | #ffffff, #252729 |
| `--card`, `--popover` | `--surface`, `--surface-raised` | #ffffff |
| `--primary`, `--primary-foreground` | `--accent`, `--accent-foreground` | #2563eb, #ffffff |
| `--secondary`, `--muted` | `--surface-muted` | #f5f7fa |
| `--muted-foreground` | `--text-secondary` | #6b7682 |
| `--accent` | `--accent-subtle` 또는 `--surface-muted` | 강조색 12% 또는 #f5f7fa |
| `--destructive` | `--danger` | #e35462 |
| `--border`, `--input` | `--border`, `--border-strong` | #dfe5eb, #b7bfc7 |
| `--ring` | `--accent` 72% | |
| `--chart-1`~`4` | `--series-1`~`4` | 3.1 표 |
| `--radius` | 대응 없음 | 이 사이트는 18, 26, 34px 세 단계 |

주의할 점은 `--accent`의 뜻이 다르다는 것이다. shadcn/ui에서는 올렸을 때의 옅은 면이고, 이 사이트에서는 브랜드 강조색이다. 하이라이트(노랑)와 색 그림자는 shadcn/ui에 대응하는 변수가 없다.

## 8. 리드미에 가져오는 방법

| 선택지 | 하는 일 | 얻는 것 | 치르는 것 |
|---|---|---|---|
| A. 값과 구조만 참고 | shadcn/ui를 그대로 쓰고 리드미 CSS 변수를 같은 역할 체계로 직접 정한다 | 이미 정한 "고유 디자인 시스템 + shadcn/ui"와 그대로 맞는다. 색과 모서리를 리드미 방향에 맞게 고른다 | 헤더 독, 세그먼트 탭, 톤 카드, 크기 반응을 직접 만든다 |
| B. Arc/dsui 설치 | `npx shadcn@latest add https://dsui.vercel.app/r/<이름>.json`으로 컴포넌트와 `foundation.css`를 받고 색·모서리만 덮어쓴다 | 이 사이트와 같은 부품을 바로 쓴다. React 19와 CSS Modules라 Vite에서 돌고 Tailwind가 필요 없다 | shadcn/ui 기본 컴포넌트와 스타일 방식이 달라진다. 버튼이 효과음 항목(`arc-sound`)을 함께 설치한다. 설치할 수 있는 항목이 일부다. 결과물이 이 사이트와 닮는다 |
| C. 섞어 쓰기 | shadcn/ui를 기본으로 두고 필요한 부품만 dsui에서 가져온다 | 폼과 표는 shadcn/ui, 헤더와 탭은 dsui | 변수 체계 둘을 하나로 맞춰야 한다. `--accent`의 뜻이 서로 다르다 |

B와 C에서 알아 둘 것:

- `shadcn init`이 만든 `--background`, `--foreground`, `--accent`, `--accent-foreground`, `--border` 다섯 줄이 `foundation.css`의 같은 이름을 덮는다. dsui 설치 문서는 이 줄을 지우거나 테마를 설치하라고 적는다.
- 색을 바꾸려면 `foundation.css` 뒤에서 `--accent`, `--accent-foreground`를 다시 정한다. 버튼, 스위치, 체크박스, 차트 1번 색이 따라 바뀐다.
- 라이선스: 레지스트리의 `arc-foundation` 항목에 든 LICENSE는 MIT(저작권 표시 Elia Kuratli)로 읽혔다. uiarc.dev에는 무료 범위와 유료 Arc Pro가 따로 있다. 둘 다 페이지 요약 도구로 읽은 것이라, 쓰기 전에 설치된 `components/arc/LICENSE` 원문과 uiarc.dev의 License 페이지를 확인해야 한다.
- 로고, 일러스트, 예시 이미지는 레지스트리에 없다. devs7udio의 것이라 가져다 쓸 수 없다. Pretendard는 SIL OFL이라 함께 배포할 수 있다.

어느 선택지든 알아 둘 것: 이 사이트 홈은 읽을거리 중심이라 폼, 표, 대시보드 화면이 없다. 그 수치는 dsui 문서의 input, select, data table, sign-in, user menu 항목에서 봐야 한다.

## 9. 확인하지 못한 것

- 홈 한 장만 봤다. 글 상세, 랭킹, 검색, 로그인 화면은 보지 않았다.
- 데스크톱의 트렌드·딥다이브 구간은 화면 캡처가 멈춰서 눈으로 보지 못했다. 이 구간의 수치는 CSS 규칙과 요소 크기로 읽은 것이다.
- 홈 화면용 CSS 약 41,000자 중 앞의 22,000자를 읽었다. 뒤쪽(랭킹 카드의 크기별 변형)은 읽지 않았다. 표의 픽셀 수치는 화면에서 잰 값이라 영향이 없지만, 대표 카드 제목처럼 규칙의 식과 잰 값이 다른 곳은 뒤쪽 규칙이 덮은 것으로 보인다.
- 창을 390px로 줄일 수 없어서, 같은 페이지를 390px 폭 프레임에 띄워 모바일 수치를 쟀다.
- 다크 모드는 변수 값만 읽었고 화면은 보지 않았다.
- dsui 컴포넌트를 실제로 설치해 보지 않았다.

## 10. 출처

- 분석 대상: https://devs7ud.io/
- dsui 항목 목록: https://dsui.vercel.app/llms.txt
- dsui 설치 문서: https://dsui.vercel.app/docs/installation
- dsui 테마 문서: https://dsui.vercel.app/docs/theming.md
- Arc 기반 항목(라이선스 포함): https://dsui.vercel.app/r/arc-foundation.json
- Arc: https://uiarc.dev
