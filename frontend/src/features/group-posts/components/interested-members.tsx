import { PlaceholderLink } from "@/shared/components/placeholder-link"
import type { Disclosure, GroupPostDetail, Member } from "@/features/group-posts/types"
import { cn } from "@/shared/lib/utils"

import { AppliedBadge } from "./chips"

export const DISCLOSURE_LABELS: Record<Disclosure, string> = {
  interest: "관심만",
  applied: "지원 여부까지",
  progress: "단계·결과까지",
}

interface InterestedMembersProps {
  post: GroupPostDetail
  viewer: Member
  myDisclosure: Disclosure
}

/** 내 줄에는 그룹 멤버에게 보이는 만큼(내 공개 범위)만 적는다. */
function myVisibleState(status: string | null, disclosure: Disclosure): string | null {
  if (!status || disclosure === "interest") return null
  if (disclosure === "progress" && status !== "지원함") return `지원함 · ${status}`
  return "지원함"
}

/** 관심 멤버: 나를 맨 위에, 지원한 멤버는 지원 표시(공개 범위만큼). */
export function InterestedMembers({ post, viewer, myDisclosure }: InterestedMembersProps) {
  const meOn = post.me.interested || post.me.status !== null
  const mine = myVisibleState(post.me.status, myDisclosure)
  const count = (meOn ? 1 : 0) + post.interests.length

  return (
    <section
      aria-label="관심 멤버"
      className="flex flex-col rounded-surface border border-border bg-surface px-6 pt-6 pb-5 shadow-resting"
    >
      <div className="mb-3 flex items-baseline justify-between gap-3">
        <h2 className="font-heading text-xl leading-display font-bold tracking-display">관심 멤버</h2>
        <span className="text-xs text-text-secondary">{count}명</span>
      </div>
      <ul className="flex flex-col">
        {meOn && (
          <MemberRow name={`${viewer.name} (나)`} initial={viewer.name.slice(0, 1)} me state={mine} />
        )}
        {post.interests.map((i) => (
          <MemberRow
            key={i.member.id}
            name={i.member.name}
            initial={i.member.name.slice(0, 1)}
            state={i.applied ? (i.progress ? `지원함 · ${i.progress}` : "지원함") : null}
          />
        ))}
        {count === 0 && (
          <li className="border-t border-border-subtle py-2.5 text-label-sm text-text-secondary">아직 없음</li>
        )}
      </ul>
      <p className="mt-3 border-t border-border-subtle pt-3 text-label-sm text-text-secondary">
        지원 상태는 멤버마다 이 그룹에 정한 공개 범위만큼 보입니다. 내 공개 범위는 {DISCLOSURE_LABELS[myDisclosure]}입니다.{" "}
        {/* 공개 범위 바꾸기: 아직 화면이 없다 */}
        <PlaceholderLink className="font-bold whitespace-nowrap text-primary underline">공개 범위 바꾸기</PlaceholderLink>
      </p>
    </section>
  )
}

function MemberRow({ name, initial, state, me = false }: { name: string; initial: string; state: string | null; me?: boolean }) {
  return (
    <li className="flex items-center gap-3 border-t border-border-subtle py-2.5">
      <span
        aria-hidden="true"
        className={cn(
          "grid size-11 flex-none place-items-center rounded-full text-sm font-bold",
          me ? "bg-highlight text-highlight-foreground" : "bg-surface-sunken"
        )}
      >
        {initial}
      </span>
      <span className="min-w-0 flex-1 text-base font-semibold">{name}</span>
      {state ? (
        <AppliedBadge className="h-6 flex-none text-xs font-semibold">{state}</AppliedBadge>
      ) : (
        <span className="flex-none text-label-sm text-text-secondary">관심</span>
      )}
    </li>
  )
}
