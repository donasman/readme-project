import { joinClasses } from "@/shared/lib/utils"

import { groupColor } from "./color"

const SIZES = {
  24: "size-6 rounded-[7px] text-[11px]",
  28: "size-7 rounded-[8px] text-xs",
  32: "size-8 rounded-[10px] text-label-sm",
  40: "size-10 rounded-[12px] text-sm",
  48: "size-12 rounded-[14px] text-[20px]",
} as const

/**
 * 그룹 표시(11차 시안): 그룹 색 둥근 네모에 표시 글자. 사람(Avatar, 동그라미)과 모양으로 나눈다.
 * 그룹 색은 두 테마 모두 짙어서 글자는 늘 흰색이다. 꾸밈이라 읽지 않는다(그룹 이름은 옆 글자가 알린다).
 */
export function GroupMark({ group, size = 32, className }: { group: { id: string; mark: string }; size?: keyof typeof SIZES; className?: string }) {
  return (
    <span
      aria-hidden="true"
      style={{ backgroundColor: groupColor(group.id) }}
      className={joinClasses("grid flex-none place-items-center font-bold text-white", SIZES[size], className)}
    >
      {group.mark}
    </span>
  )
}
