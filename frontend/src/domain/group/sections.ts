/** 그룹 화면의 메뉴. 헤더가 지금 메뉴를 표시한다. */
export type GroupSection = "posts" | "members"

/** 그룹 아래 라우트의 handle. 기능의 라우트가 붙이고, 헤더가 useMatches()로 읽는다. */
export interface GroupRouteHandle {
  section: GroupSection
}

/** 라우트 handle에서 그룹 메뉴를 꺼낸다. 없으면 undefined. */
export function sectionOf(handle: unknown): GroupSection | undefined {
  return (handle as Partial<GroupRouteHandle> | undefined)?.section
}
