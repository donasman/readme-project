import type { HTMLAttributes } from "react"

import { joinClasses } from "@/shared/lib/utils"

/** 화면의 본문 틀(main): 가운데 최대 1240px, 좌우 16px, 아래 64px, 세로로 쌓는다. 위 여백과 간격은 className으로 준다. */
export function Page({ className, ...props }: HTMLAttributes<HTMLElement>) {
  return <main className={joinClasses("mx-auto flex max-w-[1240px] flex-col px-4 pb-16", className)} {...props} />
}
