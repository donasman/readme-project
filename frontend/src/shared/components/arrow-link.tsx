import type { ReactNode } from "react"
import { Link } from "react-router"

import { ArrowRightIcon } from "@/shared/icons"
import { cn } from "@/shared/lib/utils"

/** 화살표 링크: 굵은 강조색 글(14px)과 오른쪽 화살표. "내 공고 모두 보기 →" 같은 다음 화면으로 가는 길. 높이 등은 className으로 바꾼다. */
export function ArrowLink({ to, className, children }: { to: string; className?: string; children: ReactNode }) {
  return (
    <Link to={to} className={cn("inline-flex min-h-control-md items-center gap-1 text-sm font-bold whitespace-nowrap text-primary", className)}>
      <span>{children}</span>
      <ArrowRightIcon aria-hidden="true" className="size-4" />
    </Link>
  )
}
