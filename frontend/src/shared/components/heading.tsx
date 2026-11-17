import type { HTMLAttributes, ReactNode } from "react"

import { joinClasses } from "@/shared/lib/utils"

interface HeadingProps extends HTMLAttributes<HTMLHeadingElement> {
  /** 제목 단계. 기본 h2 */
  level?: 1 | 2 | 3
  /** 글자 크기 클래스. 기본 text-xl(카드 제목) */
  size?: string
}

/** 앞 화면에서 들어온 화면(카드 상세, 공고 올리기 …)의 제목: 32px, 줄 간격 1.15. h1에 그대로 붙인다. */
export const subpageTitle = "font-heading text-[32px] leading-[1.15] font-bold tracking-display"

/** 패널과 칸 묶음의 제목(18px 굵게): "확인 필요 3", "STAR", "내 메모" …. h2·h3에 붙인다. */
export const sectionTitle = "text-lg font-bold"

/** 칸 이름(13px 굵은 회색): "상황", "태그", "지워지는 것" …. */
export const subLabel = "text-label-sm font-bold text-text-secondary"

/** 제목: 제목 글꼴, 굵게, 제목용 줄 간격과 자간. */
export function Heading({ level = 2, size = "text-xl", className, ...props }: HeadingProps) {
  const Tag = `h${level}` as "h1" | "h2" | "h3"
  return (
    <Tag className={joinClasses("font-heading leading-display font-bold tracking-display", size, className)} {...props} />
  )
}

/** 패널 머리: 제목(h2, 18px 굵게)과 그 아래 회색 설명. id는 패널의 aria-labelledby가 가리킨다. */
export function PanelHead({ id, title, children }: { id: string; title: ReactNode; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <h2 id={id} className={sectionTitle}>
        {title}
      </h2>
      <p className="text-sm text-text-secondary">{children}</p>
    </div>
  )
}
