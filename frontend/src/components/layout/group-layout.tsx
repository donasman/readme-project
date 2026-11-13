import { Outlet, useLoaderData, type LoaderFunctionArgs } from "react-router"

import { GroupHeader } from "@/components/layout/group-header"
import { getMyGroups } from "@/features/group-posts/api"
import { NotFoundError } from "@/shared/lib/errors"

/** 그룹 화면들의 틀이 받는 것: 보는 사람, 내 그룹들, 지금 그룹. */
export async function groupLoader({ params }: LoaderFunctionArgs) {
  const { viewer, groups } = await getMyGroups()
  const group = groups.find((g) => g.id === params.groupId)
  if (!group) throw new NotFoundError("그룹")
  return { viewer, groups, group }
}

/** 그룹 화면들이 함께 쓰는 틀: 머리와 그 아래 화면. 지금은 공고 화면만 있다. */
export function GroupLayout() {
  const { viewer, groups, group } = useLoaderData<typeof groupLoader>()
  return (
    // 디자인 화면의 뿌리와 같은 글자 규칙: 본문 줄 간격과 자간, 한글 낱말 단위 줄바꿈, 끝줄 고르기.
    <div className="@container min-h-svh bg-background text-base leading-body tracking-body break-keep text-pretty text-foreground">
      <GroupHeader viewer={viewer} groups={groups} group={group} section="posts" />
      <Outlet />
    </div>
  )
}
