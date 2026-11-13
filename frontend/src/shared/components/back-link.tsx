import type { ReactNode } from "react"
import { Link } from "react-router"

import { ArrowLeftIcon } from "@/shared/icons"

/** 화면 맨 위의 돌아가는 링크: 왼쪽 화살표와 앞 화면 이름(14px 굵은 회색). */
export function BackLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="inline-flex min-h-control-md items-center gap-1.5 self-start text-sm font-bold text-text-secondary">
      <ArrowLeftIcon aria-hidden="true" className="size-4" />
      <span>{children}</span>
    </Link>
  )
}
