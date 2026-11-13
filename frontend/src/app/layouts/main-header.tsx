import { Link, useMatches } from "react-router"

import { Button } from "@/components/ui/button"
import { LogoMark } from "@/shared/components/logo-mark"
import { PlaceholderLink } from "@/shared/components/placeholder-link"
import { mainNavOf, type MainNav } from "@/shared/lib/main-nav"
import { paths } from "@/shared/lib/paths"
import { cn } from "@/shared/lib/utils"

const navLink = "inline-flex min-h-control-sm items-center rounded-full px-4 text-sm font-bold whitespace-nowrap"
const navState = (on: boolean) =>
  on ? "bg-accent-subtle text-accent-strong" : "text-text-secondary hover:bg-surface-muted hover:text-foreground"

/**
 * 그룹 밖 화면들의 위쪽 헤더(시안 3종의 "A · 카드 목록" 헤더): 로고, 스터디·정보공유·경험카드, 로그아웃.
 * 지금 메뉴는 아래 라우트의 handle.mainNav로 정한다(기능의 routes.tsx가 붙인다).
 */
export function MainHeader() {
  const current: MainNav | undefined = useMatches()
    .map((match) => mainNavOf(match.handle))
    .findLast((nav) => nav !== undefined)

  return (
    <header className="bg-background">
      <div className="flex min-h-18 flex-wrap items-center gap-x-5 gap-y-2 border-b border-border-subtle bg-surface px-6 py-2">
        <Link to={paths.home()} aria-label="리드미 홈" className="inline-flex flex-none items-center gap-2 text-base font-bold text-foreground">
          <LogoMark size={26} />
          <span>리드미</span>
        </Link>
        <nav aria-label="주요 메뉴" className="min-w-0">
          <ul className="flex flex-wrap items-center gap-1">
            <li>
              {/* 스터디: 지금은 내 첫 그룹의 공고 목록 */}
              <Link to={paths.home()} aria-current={current === "study" ? "page" : undefined} className={cn(navLink, navState(current === "study"))}>
                스터디
              </Link>
            </li>
            <li>
              {/* 정보공유: 아직 화면이 없다 */}
              <PlaceholderLink aria-current={current === "info" ? "page" : undefined} className={cn(navLink, navState(current === "info"))}>
                정보공유
              </PlaceholderLink>
            </li>
            <li>
              <Link to={paths.cards()} aria-current={current === "cards" ? "page" : undefined} className={cn(navLink, navState(current === "cards"))}>
                경험카드
              </Link>
            </li>
          </ul>
        </nav>
        <div className="flex min-w-0 flex-1 items-center justify-end gap-2">
          {/* 로그아웃: 서버가 없어 아직 동작 없음 */}
          <Button type="button" variant="outline" size="sm" className="flex-none">
            로그아웃
          </Button>
        </div>
      </div>
    </header>
  )
}
