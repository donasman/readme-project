import { DISCLOSURE_LABELS } from "@/domain/group/disclosure"
import type { Disclosure } from "@/domain/group/types"
import type { Viewer } from "@/domain/session/types"
import { Avatar } from "@/shared/components/avatar"
import { Heading } from "@/shared/components/heading"
import { PanelNote } from "@/shared/components/panel"
import { SurfaceCard } from "@/shared/components/surface-card"
import { textLink } from "@/shared/components/text-link"
import { firstChar, joinClasses } from "@/shared/lib/utils"

import { isInterestedOrApplied } from "../my-state"
import type { GroupPostDetail } from "../types"
import { AppliedBadge } from "./chips"

interface InterestedMembersProps {
  post: GroupPostDetail
  viewer: Viewer
  myDisclosure: Disclosure
  /** "공개 범위 바꾸기": 공개 범위 창을 연다 */
  onChangeDisclosure: () => void
}

/** 내 줄에는 그룹 멤버에게 보이는 만큼(내 공개 범위)만 적는다. */
function myVisibleState(status: string | null, disclosure: Disclosure): string | null {
  if (!status || disclosure === "interest") return null
  if (disclosure === "progress" && status !== "지원함") return `지원함 · ${status}`
  return "지원함"
}

/** 관심 멤버: 나를 맨 위에, 지원한 멤버는 지원 표시(공개 범위만큼). */
export function InterestedMembers({ post, viewer, myDisclosure, onChangeDisclosure }: InterestedMembersProps) {
  const meOn = isInterestedOrApplied(post.me)
  const mine = myVisibleState(post.me.status, myDisclosure)
  const count = (meOn ? 1 : 0) + post.interests.length

  return (
    <SurfaceCard aria-label="관심 멤버" className="flex flex-col px-6 pt-6 pb-5">
      <div className="mb-3 flex items-baseline justify-between gap-3">
        <Heading>관심 멤버</Heading>
        <span className="text-xs text-text-secondary">{count}명</span>
      </div>
      <ul className="flex flex-col">
        {meOn && (
          <MemberRow name={`${viewer.name} (나)`} initial={firstChar(viewer.name)} me state={mine} />
        )}
        {post.interests.map((i) => (
          <MemberRow
            key={i.member.id}
            name={i.member.name}
            initial={firstChar(i.member.name)}
            state={i.applied ? (i.progress ? `지원함 · ${i.progress}` : "지원함") : null}
          />
        ))}
        {count === 0 && (
          <li className="border-t border-border-subtle py-2.5 text-label-sm text-text-secondary">아직 없음</li>
        )}
      </ul>
      <PanelNote className="mt-3 pt-3">
        지원 상태는 멤버마다 이 그룹에 정한 공개 범위만큼 보입니다. 내 공개 범위는 {DISCLOSURE_LABELS[myDisclosure]}입니다.{" "}
        <button type="button" onClick={onChangeDisclosure} className={joinClasses(textLink, "cursor-pointer whitespace-nowrap")}>
          공개 범위 바꾸기
        </button>
      </PanelNote>
    </SurfaceCard>
  )
}

function MemberRow({ name, initial, state, me = false }: { name: string; initial: string; state: string | null; me?: boolean }) {
  return (
    <li className="flex items-center gap-3 border-t border-border-subtle py-2.5">
      <Avatar size={44} tone={me ? "highlight" : "sunken"}>
        {initial}
      </Avatar>
      <span className="min-w-0 flex-1 text-base font-semibold">{name}</span>
      {state ? (
        <AppliedBadge className="h-6 flex-none text-xs font-semibold">{state}</AppliedBadge>
      ) : (
        <span className="flex-none text-label-sm text-text-secondary">관심</span>
      )}
    </li>
  )
}
