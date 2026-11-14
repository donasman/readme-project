import { Link } from "react-router"

import { paths } from "@/shared/lib/paths"
import { joinClasses } from "@/shared/lib/utils"

interface PostingLinkProps {
  /** 내 공고 id */
  id: string
  company: string
  title: string
  /** 줄 안의 폭이나 자리(flex-[…], [grid-area:…]) */
  className?: string
}

/** 내 공고 상세로 가는 두 줄 링크: 위에 회사(13px 회색), 아래에 공고명(굵게). */
export function PostingLink({ id, company, title, className }: PostingLinkProps) {
  return (
    <Link to={paths.myPost(id)} className={joinClasses("flex min-w-0 flex-col gap-0.5 text-foreground", className)}>
      <span className="text-label-sm text-text-secondary">{company}</span>
      <span className="font-bold">{title}</span>
    </Link>
  )
}
