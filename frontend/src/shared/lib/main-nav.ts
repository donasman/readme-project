/** 그룹 밖 화면들의 위쪽 헤더 메뉴(스터디, 경험카드). 정보공유는 화면이 생기면 더한다. 헤더가 지금 메뉴를 표시한다. */
export type MainNav = "study" | "cards"

/** 위쪽 헤더 아래 라우트의 handle. 기능의 라우트가 붙이고, 헤더가 useMatches()로 읽는다. */
export interface MainRouteHandle {
  mainNav: MainNav
}

/** 라우트 handle에서 위쪽 헤더 메뉴를 꺼낸다. 없으면 undefined. */
export function mainNavOf(handle: unknown): MainNav | undefined {
  return (handle as Partial<MainRouteHandle> | undefined)?.mainNav
}
