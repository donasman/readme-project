import { Link } from "react-router"

import { joinClasses } from "@/shared/lib/utils"
import { paths } from "@/shared/lib/paths"

import { PlaceholderLink } from "./placeholder-link"

/** 내 공간의 메뉴. 헤더는 그대로 두고 홈 아래에 한 줄을 더 둔다(시안 8차의 제안). */
export type MySpace = "home" | "posts" | "board" | "calendar" | "mail"

const ITEMS: { key: MySpace; label: string; to: string | null }[] = [
  { key: "home", label: "홈", to: paths.home() },
  { key: "posts", label: "내 공고", to: paths.myPosts() },
  { key: "board", label: "지원 현황", to: paths.board() },
  { key: "calendar", label: "달력", to: paths.calendar() },
  // 메일 확인함: 9차에서 화면이 생긴다
  { key: "mail", label: "메일 확인함", to: null },
]

const item = (on: boolean) =>
  joinClasses(
    "inline-flex min-h-control-sm flex-none items-center rounded-full px-4 text-sm font-bold whitespace-nowrap",
    on ? "bg-accent-subtle text-accent-strong" : "text-text-secondary hover:bg-surface-muted hover:text-foreground"
  )

/** 홈, 내 공고, 지원 현황, 달력, 메일 확인함. 좁으면 옆으로 민다. */
export function MySpaceNav({ current }: { current: MySpace }) {
  return (
    <nav aria-label="내 공간" className="-mx-2 -mt-2 flex gap-1 overflow-x-auto px-2 [scrollbar-width:none]">
      {ITEMS.map(({ key, label, to }) =>
        to ? (
          <Link key={key} to={to} aria-current={key === current ? "page" : undefined} className={item(key === current)}>
            {label}
          </Link>
        ) : (
          <PlaceholderLink key={key} aria-current={key === current ? "page" : undefined} className={item(key === current)}>
            {label}
          </PlaceholderLink>
        )
      )}
    </nav>
  )
}
