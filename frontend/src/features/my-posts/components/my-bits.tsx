import { Pill } from "@/shared/components/pill"
import { joinClasses } from "@/shared/lib/utils"

import { isActive, statusLabel } from "../status"
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

/** 큰 숫자 한 칸(28px). dl 안에 둔다. highlight는 확인할 것이 남은 칸. */
export function BigStat({ label, value, highlight = false }: { label: string; value: number; highlight?: boolean }) {
  return (
    <div
      className={joinClasses(
        "flex flex-col gap-1 rounded-control border px-5 py-4",
        highlight ? "border-transparent bg-highlight-subtle" : "border-border bg-surface"
      )}
    >
      <dt className={joinClasses("text-label-sm", highlight ? "text-foreground" : "text-text-secondary")}>{label}</dt>
      <dd className="text-[28px] leading-[1.1] font-bold tracking-[-0.02em]">{value}</dd>
    </div>
  )
}

/** 내 공간 화면의 본문 틀: 1120px, 위 40px, 세로 24px 간격. */
export const myMain = "mx-auto flex max-w-[1120px] flex-col gap-6 px-6 pt-10 pb-16"
