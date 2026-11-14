import type { HTMLAttributes } from "react"

import { joinClasses } from "@/shared/lib/utils"

interface PanelProps extends HTMLAttributes<HTMLElement> {
  /** 요소 종류. 기본은 section */
  as?: "section" | "article" | "div" | "ul" | "li"
}

/**
 * 패널: 모서리 radius-panel(26px), 테두리, 면 색, 쉬는 그림자. 경험카드 화면들의 카드와 칸이 쓴다.
 * 그룹 공고 화면의 큰 면(radius-surface)은 SurfaceCard다. 안쪽 여백과 배치는 className으로 준다.
 */
export function Panel({ as: Tag = "section", className, ...props }: PanelProps) {
  return <Tag className={joinClasses("rounded-panel border border-border bg-surface shadow-resting", className)} {...props} />
}

/** 패널 맨 아래 덧말: 가는 줄 위 13px 회색 글. 줄과의 간격(pt-…)은 className으로 준다. */
export function PanelNote({ className, ...props }: HTMLAttributes<HTMLParagraphElement>) {
  return <p className={joinClasses("border-t border-border-subtle text-label-sm text-text-secondary", className)} {...props} />
}
