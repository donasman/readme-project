# 그룹 공고 화면 디자인 (2026-11-13)

캔버스에서 그린 그룹 공고 목록과 그룹 공고 상세의 원본과 캡처다. 화면을 코드로 옮길 때 기준으로 쓴다.
무엇을 왜 이렇게 정했는지는 `docs/리드미_리서치_재사용_정리.md`의 "그룹 공고 (2026-11-13에 정함)" 절에 있다.

## 파일

| 파일 | 내용 |
|---|---|
| `group-posts.dc.html` | 그룹 공고 목록. 헤더, 그룹 전환 메뉴, 필터, 정렬, 검색, 목록 줄 |
| `group-post.dc.html` | 그룹 공고 상세. 마감 카드, 공고 내용, 관심 멤버, 댓글 |
| `readme-frame.css` | 틀 변수. `frontend/src/components/arc/foundation.css` 값 그대로이고 강조색과 하이라이트만 빠져 있다 |
| `readme-tabs.css` | 색 A 값(`.pal-a`)과 좁은 화면 규칙. `.pal-c`~`.pal-i`는 버린 색 시안이라 보지 않는다 |
| `shots/` | 캡처. 데스크톱 1280px, 모바일 390px(2배 해상도) |

캡처 목록:

- `group-posts-desktop.png`, `group-posts-mobile.png`: 목록 기본 상태
- `group-posts-desktop-group-menu.png`: 헤더의 그룹 전환 메뉴를 연 상태
- `group-posts-desktop-closed.png`: "마감됨" 탭
- `group-posts-desktop-dark.png`: 다크
- `group-post-desktop.png`, `group-post-mobile.png`: 상세 기본 상태
- `group-post-desktop-leader.png`: 올린 사람·그룹장이 볼 때("그룹에서 내리기", 남의 댓글 "삭제"가 보인다)
- `group-post-desktop-dark.png`: 다크

## .dc.html 읽는 법

캔버스 전용 형식이라 브라우저에서 바로 열리지 않는다. 글자로 읽는다.

- 모양의 기준은 각 요소의 인라인 `style`이다. `var(--…)`는 프런트의 토큰과 이름이 같다.
- `{{이름}}`은 데이터가 들어가는 자리다.
- `<sc-for list="{{목록}}" as="항목">`은 반복, `<sc-if value="{{조건}}">`은 조건이다.
- 파일 끝 `<script>`의 `renderVals()`가 예시 데이터와 필터·정렬·묶음 규칙이다. `groups()`가 예시 데이터다.
- `class="rm-…"`는 `readme-tabs.css`의 좁은 화면 규칙이 붙는 자리다. 캔버스에서는 컨테이너 폭 768px 미만, 앱에서는 화면 폭 768px 미만(`md` 아래)에 해당한다.
  - `rm-nav`: 좁으면 숨는 헤더 메뉴. `rm-menu`: 좁을 때만 보이는 메뉴 버튼.
  - `rm-row`와 `rm-c-*`: 목록 한 줄. 넓으면 칸 6개, 좁으면 카드(회사명과 D-day / 관심 멤버와 댓글 수 / 내 상태). `rm-head`(칸 이름 줄)와 `rm-c-by`(올린 사람)는 좁으면 숨는다.

## 알아 둘 것

- 색은 A안이다. 강조색 `#1e305e`(다크 `#c8d8fa`), 강조색 위 글자 `#ffffff`(다크 `#0e1526`), 하이라이트 `#f7ee53`(다크 `#eae142`), 하이라이트 진한 값 `#dccf3a`(다크 `#f3ee82`), 하이라이트 위 글자 `#252729`, 다크 `control-on` `#5b73aa`.
- 하이라이트색은 "내 것"에만 쓴다(나 칩, 관심 중, 내 관심만). "새 공고"는 강조색 배지다.
- 로고는 아직 없어 글자 "리드미"만 두었다. 아이콘은 lucide를 임시로 썼다. 둘 다 나중에 바꾼다.
- 헤더는 Arc `app-header`가 스크롤됐을 때의 값(바깥 여백 12px 16px, 모서리 `radius-panel`, `shadow-floating`)으로 그렸다.
- 그룹 이름, 회사, 공고, 멤버, 댓글은 전부 지어낸 예시다. D-day는 2026-11-13 기준이다.
- 보는 사람은 "다온"으로 두었다. 지원함 표시는 공개 범위가 "지원 여부까지" 이상인 멤버에게만 붙는다.
