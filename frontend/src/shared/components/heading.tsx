import type { HTMLAttributes } from "react"

import { joinClasses } from "@/shared/lib/utils"

interface HeadingProps extends HTMLAttributes<HTMLHeadingElement> {
  /** 제목 단계. 기본 h2 */
  level?: 1 | 2 | 3
  /** 글자 크기 클래스. 기본 text-xl(카드 제목) */
  size?: string
}

/** 앞 화면에서 들어온 화면(카드 상세, 공고 올리기 …)의 제목: 32px, 줄 간격 1.15. h1에 그대로 붙인다. */
export const subpageTitle = "font-heading text-[32px] leading-[1.15] font-bold tracking-display"

/** 제목: 제목 글꼴, 굵게, 제목용 줄 간격과 자간. */
export function Heading({ level = 2, size = "text-xl", className, ...props }: HeadingProps) {
  const Tag = `h${level}` as "h1" | "h2" | "h3"
  return (
    <Tag className={joinClasses("font-heading leading-display font-bold tracking-display", size, className)} {...props} />
  )
}
