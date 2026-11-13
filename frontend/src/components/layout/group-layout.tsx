import { Outlet } from "react-router"

import { GroupHeader } from "@/components/layout/group-header"
import { useGroup } from "@/domain/group/use-group"
import { useViewer } from "@/domain/session/use-viewer"

/** 그룹 화면들이 함께 쓰는 틀: 머리와 그 아래 화면. 지금은 공고 화면만 있다. */
export function GroupLayout() {
  const viewer = useViewer()
  const { groups, group } = useGroup()
  return (
    // 디자인 화면의 뿌리와 같은 글자 규칙: 본문 줄 간격과 자간, 한글 낱말 단위 줄바꿈, 끝줄 고르기.
    <div className="@container min-h-svh bg-background text-base leading-body tracking-body break-keep text-pretty text-foreground">
      <GroupHeader viewer={viewer} groups={groups} group={group} section="posts" />
      <Outlet />
    </div>
  )
}
