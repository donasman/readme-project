import { useState } from "react"
import { Link, useLoaderData, useLocation, useNavigate } from "react-router"

import { Button } from "@/components/ui/button"
import type { Group } from "@/domain/group/types"
import { useViewer } from "@/domain/session/use-viewer"
import { Avatar } from "@/shared/components/avatar"
import { Heading } from "@/shared/components/heading"
import { Page } from "@/shared/components/page"
import { pageHead } from "@/shared/components/page-layout"
import { Panel } from "@/shared/components/panel"
import { PlusIcon, UsersIcon } from "@/shared/icons"
import { formatDday } from "@/shared/lib/date"
import { paths } from "@/shared/lib/paths"

import { getGroupList } from "../api"
import { CreateGroupDialog } from "../components/create-group-dialog"
import { RoleChip } from "../components/role-chip"

export function groupsLoader() {
  return getGroupList()
}

/** 다른 화면이 "그룹 만들기"를 누르고 이 화면으로 올 때 넘기는 값. */
export interface GroupsPageState {
  create?: boolean
}

/** 내 그룹(시안 7차 "내 그룹"). 그룹이 없으면 빈 상태를 보여 준다. */
export function GroupsPage() {
  const { today, groups } = useLoaderData<typeof groupsLoader>()
  const location = useLocation()
  const navigate = useNavigate()
  const [creating, setCreating] = useState(() => (location.state as GroupsPageState | null)?.create === true)

  function changeCreating(open: boolean) {
    setCreating(open)
    // 그룹 헤더에서 "그룹 만들기"로 왔으면, 닫은 뒤 새로 고쳐도 다시 열리지 않게 주소의 값을 지운다
    if (!open && location.state) navigate(".", { replace: true, state: null })
  }

  const createButton = (
    <Button type="button" className="flex-none" onClick={() => setCreating(true)}>
      <PlusIcon data-icon="inline-start" aria-hidden="true" />
      그룹 만들기
    </Button>
  )

  return (
    <Page narrow className="gap-8 pt-10">
      <div className={pageHead}>
        <div className="flex min-w-0 flex-col gap-2">
          <Heading level={1} size="text-3xl">
            내 그룹
          </Heading>
          <p className="text-text-secondary">같이 준비하는 사람들과 공고를 모으는 곳입니다.</p>
        </div>
        {createButton}
      </div>

      {groups.length > 0 ? (
        <ul className="grid grid-cols-[repeat(auto-fit,minmax(min(320px,100%),1fr))] gap-5">
          {groups.map((group) => (
            <li key={group.id} className="flex">
              <GroupCard group={group} today={today} />
            </li>
          ))}
        </ul>
      ) : (
        <section
          aria-labelledby="groups-empty"
          className="flex flex-col items-start gap-4 rounded-panel border-[1.5px] border-dashed border-border-strong px-8 py-10"
        >
          <UsersIcon aria-hidden="true" className="size-7 text-text-secondary" />
          <div className="flex flex-col gap-2">
            <h2 id="groups-empty" className="text-lg font-bold">
              아직 들어간 그룹이 없습니다
            </h2>
            <p className="text-text-secondary">그룹을 만들어 사람들을 초대하거나, 받은 초대 링크를 열어 들어가세요.</p>
          </div>
          {createButton}
        </section>
      )}

      <CreateGroupDialog open={creating} onOpenChange={changeCreating} />
    </Page>
  )
}

/** 그룹 한 칸: 표시 글자, 이름, 멤버·공고 수, 그룹장 표시, 멤버 이름, 가장 가까운 마감. 칸 전체가 그룹 공고로 가는 링크다. */
function GroupCard({ group, today }: { group: Group; today: string }) {
  const viewer = useViewer()
  const names = [...group.members.filter((m) => m.id !== viewer.id).map((m) => m.name), "나"].join(" · ")
  const next = group.nearestDeadline

  return (
    <Panel as="article" className="relative flex flex-1 flex-col gap-4 p-6">
      <div className="flex items-center gap-3">
        <Avatar size={40} tone="accent">
          {group.mark}
        </Avatar>
        <div className="flex min-w-0 flex-1 flex-col gap-0.5">
          <h2 className="text-lg font-bold">
            {/* 칸 어디를 눌러도 그룹으로 간다(링크의 ::after가 칸을 덮는다) */}
            <Link to={paths.groupPosts(group.id)} className="after:absolute after:inset-0 after:rounded-panel">
              {group.name}
            </Link>
          </h2>
          <span className="text-label-sm text-text-secondary">
            멤버 {group.members.length}명 · 진행 중 공고 {group.openPostCount}
          </span>
        </div>
        {group.leaderId === viewer.id && <RoleChip role="leader" />}
      </div>
      <p className="text-sm text-text-secondary">{names}</p>
      <div className="mt-auto flex flex-col gap-0.5 border-t border-border-subtle pt-4 text-sm">
        <span className="text-xs font-bold text-text-secondary">가장 가까운 마감</span>
        <span>{next ? `${formatDday(today, next.date)} · ${next.company} ${next.title}` : "다가오는 마감이 없습니다"}</span>
      </div>
    </Panel>
  )
}
