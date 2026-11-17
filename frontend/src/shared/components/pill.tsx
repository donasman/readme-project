import type { ReactNode } from "react"

import { joinClasses } from "@/shared/lib/utils"

const TONES = {
  /** 옅은 강조색: 그룹장, 진행 중인 지원 상태 */
  accent: "bg-accent-subtle text-accent-strong",
  /** 회색: 미정, 보류, 떨어진 지원 */
  muted: "bg-surface-muted text-foreground",
  /** 하이라이트색: 나 */
  highlight: "bg-highlight text-highlight-foreground",
} as const

/** 짧은 표시 알약(24px, 12px 굵게): "그룹장", "나", "필기 단계" …. */
export function Pill({ tone, children }: { tone: keyof typeof TONES; children: ReactNode }) {
  return <span className={joinClasses("inline-flex h-6 flex-none items-center rounded-full px-2.5 text-xs font-bold whitespace-nowrap", TONES[tone])}>{children}</span>
}
