import { Link, useMatches, useNavigate } from "react-router"

import { Button } from "@/components/ui/button"
import { GroupMark } from "@/domain/group/group-mark"
import { sectionOf } from "@/domain/group/sections"
import { useGroup } from "@/domain/group/use-group"
import { useViewer } from "@/domain/session/use-viewer"
import { createInvite, type MembersPageState } from "@/features/groups"
import { Avatar } from "@/shared/components/avatar"
import { LinkIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { firstChar, joinClasses } from "@/shared/lib/utils"

const tab = (on: boolean) =>
  joinClasses(
    "inline-flex min-h-11 w-full items-center justify-center gap-1.5 border-b-[3px] text-base font-bold sm:w-auto",
    on ? "border-primary text-foreground" : "border-transparent text-text-secondary hover:text-foreground"
  )

/**
 * 그룹 머리 영역(11차 시안): 그룹 표시와 이름, 멤버, 초대 링크 만들기, 공고·멤버 탭. 바탕은 옅은 회색(surface-muted)이다.
 * 그룹의 모든 화면 위에 둔다. 화면의 제목(h1)은 각 화면에 있어서(공고 상세는 공고명) 그룹 이름은 제목으로 두지 않는다.
 * 초대 링크는 만든 뒤 멤버 화면으로 들고 간다(그룹을 만든 뒤와 같은 길). 지금 탭은 라우트의 handle.section으로 정한다.
 */
export function GroupBand() {
  const { group } = useGroup()
  const viewer = useViewer()
  const navigate = useNavigate()
  const section = useMatches()
    .map((match) => sectionOf(match.handle))
    .findLast((s) => s !== undefined)
  const leader = group.members.find((m) => m.id === group.leaderId)
  // 다른 멤버의 얼굴을 먼저, 나는 맨 끝에
  const faces = [...group.members.filter((m) => m.id !== viewer.id), ...group.members.filter((m) => m.id === viewer.id)].slice(0, 5)

  async function invite() {
    const state: MembersPageState = { invite: await createInvite(group.id) }
    await navigate(paths.groupMembers(group.id), { state })
  }

  return (
    <section
      aria-label={`${group.name} 그룹`}
      className="border-b border-border-subtle bg-surface-muted"
    >
      <div className="mx-auto flex max-w-[1240px] flex-col gap-3 px-4 pt-5">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-3">
          <GroupMark group={group} size={48} />
          <div className="flex min-w-0 flex-[1_1_220px] flex-col gap-1">
            <p className="text-xl leading-display font-bold tracking-display sm:text-2xl">{group.name}</p>
            <p className="text-sm text-text-secondary">
              멤버 {group.members.length}명 · 진행 중 공고 {group.openPostCount}건{leader && ` · 그룹장 ${leader.name}`}
            </p>
          </div>
          <div aria-hidden="true" className="hidden sm:flex">
            {faces.map((m, i) => (
              <Avatar key={m.id} size={32} tone={m.id === viewer.id ? "highlight" : "sunken"} className={joinClasses("ring-2 ring-background", i > 0 && "-ml-2")}>
                {firstChar(m.name)}
              </Avatar>
            ))}
          </div>
          {/* 휴대폰에서는 아이콘만 두어 이름 옆 한 줄에 둔다(이름은 화면 읽기에 그대로 들린다) */}
          <Button type="button" variant="outline" className="flex-none max-sm:w-11 max-sm:px-0" onClick={() => void invite()}>
            <LinkIcon data-icon="inline-start" aria-hidden="true" className="max-sm:m-0" />
            <span className="max-sm:sr-only">초대 링크 만들기</span>
          </Button>
        </div>
        <nav aria-label="그룹 메뉴">
          <ul className="grid grid-cols-2 sm:flex sm:gap-7">
            <li>
              <Link to={paths.groupPosts(group.id)} aria-current={section === "posts" ? "page" : undefined} className={tab(section === "posts")}>
                공고 <span className="text-sm font-semibold text-text-secondary">{group.openPostCount}</span>
              </Link>
            </li>
            <li>
              <Link to={paths.groupMembers(group.id)} aria-current={section === "members" ? "page" : undefined} className={tab(section === "members")}>
                멤버 <span className="text-sm font-semibold text-text-secondary">{group.members.length}</span>
              </Link>
            </li>
          </ul>
        </nav>
      </div>
    </section>
  )
}
