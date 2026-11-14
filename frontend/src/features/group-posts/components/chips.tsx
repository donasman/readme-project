import type { ReactNode } from "react"

import { CheckIcon } from "@/shared/icons"
import { cn } from "@/shared/lib/utils"

const chip = "inline-flex h-6 items-center rounded-full px-2.5 text-xs whitespace-nowrap"

/** 지원함 표시: 강조색 옅은 채움 위 체크와 글자. */
export function AppliedBadge({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-0.5 rounded-full bg-accent-subtle pr-2.5 pl-1.5 text-accent-strong",
        className
      )}
    >
      <CheckIcon aria-hidden="true" className="size-3" strokeWidth={2.5} />
      <span>{children}</span>
    </span>
  )
}

/** 관심을 켠 다른 멤버. 지원한 멤버는 체크로 구분한다(공개 범위가 "지원 여부까지" 이상인 멤버만). */
export function MemberChip({ name, applied }: { name: string; applied: boolean }) {
  if (applied) {
    return (
      <AppliedBadge className="h-6 text-xs font-semibold">
        {name}
        <span className="sr-only">, 지원함</span>
      </AppliedBadge>
    )
  }
  return <span className={cn(chip, "bg-surface-muted font-semibold text-foreground")}>{name}</span>
}

/** 올린 지 3일 이내인 공고. 강조색 배지다. */
export function NewBadge() {
  return (
    <span className="inline-flex flex-none items-center rounded-full bg-primary px-2 py-px text-xs font-semibold whitespace-nowrap text-primary-foreground">
      새 공고
    </span>
  )
}

/** 내 지원 관리의 상태 값(지원함, 필기 단계 …). */
export function StatusPill({ children }: { children: ReactNode }) {
  return (
    <span className="inline-flex h-7 items-center rounded-full bg-accent-subtle px-3 text-label-sm font-bold whitespace-nowrap text-accent-strong">
      {children}
    </span>
  )
}
