import type { LoaderFunctionArgs } from "react-router"

import { NotFoundError } from "@/shared/lib/errors"

import { getMyGroups } from "./api"

/** 그룹 라우트(/groups/:groupId)의 id. useGroup()이 이 id로 loader 값을 읽는다. */
export const GROUP_ROUTE_ID = "group"

/** 그룹 라우트의 loader: 내 그룹들과 지금 그룹. 주소의 그룹이 내 그룹에 없으면 NotFoundError. */
export async function groupLoader({ params }: LoaderFunctionArgs) {
  const groups = await getMyGroups()
  const group = groups.find((g) => g.id === params.groupId)
  if (!group) throw new NotFoundError("그룹")
  return { groups, group }
}
