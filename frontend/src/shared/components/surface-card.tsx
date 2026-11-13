import type { HTMLAttributes } from "react"

import { joinClasses } from "@/shared/lib/utils"

interface SurfaceCardProps extends HTMLAttributes<HTMLElement> {
  /** 요소 종류. 기본은 section */
  as?: "section" | "div" | "article"
}

/** 면 카드: 모서리 radius-surface, 테두리, 면 색, 쉬는 그림자. 안쪽 여백과 배치는 className으로 준다. */
export function SurfaceCard({ as: Tag = "section", className, ...props }: SurfaceCardProps) {
  return <Tag className={joinClasses("rounded-surface border border-border bg-surface shadow-resting", className)} {...props} />
}
