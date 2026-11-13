import type { HTMLAttributes } from "react"

import { joinClasses } from "@/shared/lib/utils"

interface PanelProps extends HTMLAttributes<HTMLElement> {
  /** 요소 종류. 기본은 section */
  as?: "section" | "article" | "div" | "ul"
}

/**
 * 패널: 모서리 radius-panel(26px), 테두리, 면 색, 쉬는 그림자. 경험카드 화면들의 카드와 칸이 쓴다.
 * 그룹 공고 화면의 큰 면(radius-surface)은 SurfaceCard다. 안쪽 여백과 배치는 className으로 준다.
 */
export function Panel({ as: Tag = "section", className, ...props }: PanelProps) {
  return <Tag className={joinClasses("rounded-panel border border-border bg-surface shadow-resting", className)} {...props} />
}
