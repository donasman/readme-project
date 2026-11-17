import { isActive, statusLabel } from "@/domain/posting/my-state"
import { Pill } from "@/shared/components/pill"
import { joinClasses } from "@/shared/lib/utils"

import type { MyState } from "../types"

// 홈과 내 공고 화면들이 함께 쓰는 조각(시안 8차).

/** 내 지원 상태 알약: 지원해서 진행 중이면 옅은 강조색, 그 밖은 회색. */
export function MyStatusPill({ state }: { state: MyState }) {
  return <Pill tone={isActive(state) ? "accent" : "muted"}>{statusLabel(state)}</Pill>
}

/** D-day 알약(28px). 3일 안이면 진한 강조색. */
export function DdayBadge({ text, soon }: { text: string; soon: boolean }) {
  return (
    <span
      className={joinClasses(
        "inline-flex h-7 min-w-13 flex-none items-center justify-center rounded-full px-2.5 text-label-sm font-bold whitespace-nowrap",
        soon ? "bg-primary text-primary-foreground" : "bg-accent-subtle text-accent-strong"
      )}
    >
      {text}
    </span>
  )
}

/** 큰 숫자 한 칸(28px). dl 안에 둔다. */
export function BigStat({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex flex-col justify-between gap-1 rounded-control border border-border bg-surface px-5 py-4">
      <dt className="text-label-sm text-text-secondary">{label}</dt>
      <dd className="text-[28px] leading-[1.1] font-bold tracking-[-0.02em]">{value}</dd>
    </div>
  )
}
