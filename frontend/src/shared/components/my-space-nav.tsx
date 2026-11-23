import { Link } from "react-router"

import { joinClasses } from "@/shared/lib/utils"
import { paths } from "@/shared/lib/paths"

import { navPill } from "./nav-pill"

/** 내 공간의 메뉴. 헤더는 그대로 두고 홈 아래에 한 줄을 더 둔다(시안 8차의 제안). */
export type MySpace = "home" | "posts" | "board" | "calendar" | "mail"

const ITEMS: { key: MySpace; label: string; to: string }[] = [
  { key: "home", label: "오늘", to: paths.home() },
  { key: "posts", label: "내 공고", to: paths.myPosts() },
  { key: "board", label: "지원 현황", to: paths.board() },
  { key: "calendar", label: "달력", to: paths.calendar() },
  { key: "mail", label: "메일 확인함", to: paths.mail() },
]

/** 오늘(홈), 내 공고, 지원 현황, 달력, 메일 확인함. 위쪽 헤더의 "대시보드"와 겹치지 않게 첫 칸은 "오늘"이다. 좁으면 옆으로 민다. */
export function MySpaceNav({ current }: { current: MySpace }) {
  return (
    <nav aria-label="내 공간" className="-mx-2 -mt-2 flex gap-1 overflow-x-auto px-2 [scrollbar-width:none]">
      {ITEMS.map(({ key, label, to }) => (
        // 휴대폰(390px)에서 다섯 칸이 다 들어가게 칸 좌우 여백을 줄인다. 그보다 좁으면 옆으로 민다
        <Link key={key} to={to} aria-current={key === current ? "page" : undefined} className={joinClasses(navPill(key === current), "flex-none max-sm:px-2.5")}>
          {label}
        </Link>
      ))}
    </nav>
  )
}
