import type { RouteObject } from "react-router"

import type { GroupRouteHandle } from "@/domain/group/sections"
import type { MainRouteHandle } from "@/shared/lib/main-nav"

/**
 * 내 그룹 목록. 앱의 위쪽 헤더 틀 아래에 붙고, 헤더의 "스터디"가 이 화면이다(시안 7차의 제안).
 * 화면과 loader는 그 주소에 처음 들어갈 때 불러온다(lazy).
 */
export const groupsRoutes: RouteObject[] = [
  {
    path: "/groups",
    handle: { mainNav: "study" } satisfies MainRouteHandle,
    lazy: async () => {
      const { GroupsPage, groupsLoader } = await import("./pages/groups-page")
      return { loader: groupsLoader, Component: GroupsPage }
    },
  },
]

/** 멤버·초대 관리. 앱의 그룹 라우트(/groups/:groupId) 아래에 붙는다. 그룹 값은 그룹 라우트가 읽는다(useGroup). */
export const groupMembersRoutes: RouteObject[] = [
  {
    path: "members",
    handle: { section: "members" } satisfies GroupRouteHandle,
    lazy: async () => {
      const { GroupMembersPage, membersLoader } = await import("./pages/group-members-page")
      return { loader: membersLoader, Component: GroupMembersPage }
    },
  },
]
