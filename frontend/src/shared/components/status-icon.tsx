import type { ReactNode } from "react"

import { joinClasses } from "@/shared/lib/utils"

/** 결과·상태를 알리는 48px 아이콘 칸(메일 보냄, 끝남, 소개의 기능 …). accent가 기본이고 muted는 링크 만료 같은 끝난 상태다. */
export function StatusIcon({ tone = "accent", className, children }: { tone?: "accent" | "muted"; className?: string; children: ReactNode }) {
  return (
    <span
      aria-hidden="true"
      className={joinClasses(
        "inline-flex size-12 items-center justify-center rounded-control [&_svg]:size-6",
        tone === "accent" ? "bg-accent-subtle text-accent-strong" : "bg-surface-muted text-foreground",
        className
      )}
    >
      {children}
    </span>
  )
}
