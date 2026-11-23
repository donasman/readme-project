import type { HTMLAttributes } from "react"

import { joinClasses } from "@/shared/lib/utils"

interface PageProps extends HTMLAttributes<HTMLElement> {
  /** 1120px, 좌우 24px. 그룹 밖 화면(경험카드, 내 공간, 설정 …)이 쓴다. 기본은 그룹 화면의 1240px, 좌우 16px */
  narrow?: boolean
}

/** 본문 폭과 좌우 여백. 헤더(app-header.tsx)도 같은 값을 써서 로고와 본문의 왼쪽 끝을 맞춘다. */
export const pageWidth = (narrow: boolean) => (narrow ? "max-w-[1120px] px-6" : "max-w-[1240px] px-4")

/** 화면의 본문 틀(main): 가운데, 아래 64px, 세로로 쌓는다. 위 여백과 간격은 className으로 준다. */
export function Page({ narrow = false, className, ...props }: PageProps) {
  return (
    <main
      id="main"
      className={joinClasses("mx-auto flex flex-col pb-16", pageWidth(narrow), className)}
      {...props}
    />
  )
}
