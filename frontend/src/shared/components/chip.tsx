import type { HTMLAttributes } from "react"

import { joinClasses } from "@/shared/lib/utils"

interface ChipProps extends HTMLAttributes<HTMLElement> {
  /** 요소 종류. 목록 안이면 li, 아니면 span */
  as?: "li" | "span"
}

/** 태그·키워드 칩: 둥근 눌린 면에 12px 글자. 카드 태그, 공고 키워드에 쓴다. */
export function Chip({ as: Tag = "li", className, ...props }: ChipProps) {
  return <Tag className={joinClasses("rounded-full bg-surface-muted px-2.5 py-1 text-xs font-medium", className)} {...props} />
}
