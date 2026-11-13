import type { ReactNode } from "react"

import { joinClasses } from "@/shared/lib/utils"

const SIZES = {
  28: "size-7 text-xs",
  32: "size-8 text-label-sm",
  36: "size-control-sm text-label-sm",
  40: "size-10 text-sm",
  44: "size-11 text-sm",
  56: "size-14 text-[20px]",
} as const

const TONES = {
  /** 눌린 면 */
  sunken: "bg-surface-sunken text-foreground",
  /** 강조색 */
  accent: "bg-primary text-primary-foreground",
  /** 하이라이트(나) */
  highlight: "bg-highlight text-highlight-foreground",
} as const

interface AvatarProps {
  /** 동그라미 안의 글자. 보통 이름이나 그룹의 첫 글자 */
  children: ReactNode
  size?: keyof typeof SIZES
  tone?: keyof typeof TONES
  className?: string
}

/** 글자 한 자가 든 동그라미. 꾸밈이라 읽지 않는다(이름은 옆 글자나 버튼 이름이 알린다). */
export function Avatar({ children, size = 36, tone = "sunken", className }: AvatarProps) {
  return (
    <span
      aria-hidden="true"
      className={joinClasses("grid flex-none place-items-center rounded-full font-bold", SIZES[size], TONES[tone], className)}
    >
      {children}
    </span>
  )
}
