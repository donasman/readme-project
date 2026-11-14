import { joinClasses } from "@/shared/lib/utils"

/**
 * 메뉴 알약: 36px, 둥근 끝, 14px 굵게. 지금 메뉴(on)는 옅은 강조색, 아니면 회색 글자에 올리면 옅은 면.
 * 위쪽 헤더, 그룹 머리, 설정, 내 공간 메뉴가 쓴다. 클래스 묶음이라 Link, NavLink, PlaceholderLink 어디에나 붙인다.
 */
export const navPill = (on: boolean) =>
  joinClasses(
    "inline-flex min-h-control-sm items-center rounded-full px-4 text-sm font-bold whitespace-nowrap",
    on ? "bg-accent-subtle text-accent-strong" : "text-text-secondary hover:bg-surface-muted hover:text-foreground"
  )
