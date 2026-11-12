import { Outlet, type LoaderFunctionArgs, type ShouldRevalidateFunctionArgs } from "react-router"

import { getMyGroups, NotFoundError } from "@/features/group-posts/api"

/** 그룹 화면들의 틀이 받는 것: 보는 사람, 내 그룹들, 지금 그룹. */
export async function groupLoader({ params }: LoaderFunctionArgs) {
  const { viewer, groups } = await getMyGroups()
  const group = groups.find((g) => g.id === params.groupId)
  if (!group) throw new NotFoundError("그룹")
  return { viewer, groups, group }
}

export type GroupLayoutData = Awaited<ReturnType<typeof groupLoader>>

/** 검색어·필터처럼 주소의 ? 뒤만 바뀔 때는 다시 읽지 않는다. */
export function shouldRevalidateGroup({ currentUrl, nextUrl, defaultShouldRevalidate }: ShouldRevalidateFunctionArgs) {
  if (currentUrl.pathname === nextUrl.pathname && currentUrl.search !== nextUrl.search) return false
  return defaultShouldRevalidate
}

/** 그룹 화면들이 함께 쓰는 틀. */
export function GroupLayout() {
  return <Outlet />
}
