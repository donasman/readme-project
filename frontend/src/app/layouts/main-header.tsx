import { Link, useMatches } from "react-router"

import { useViewer } from "@/domain/session/use-viewer"
import { Avatar } from "@/shared/components/avatar"
import { LogoMark } from "@/shared/components/logo-mark"
import { PlaceholderLink } from "@/shared/components/placeholder-link"
import { navPill } from "@/shared/components/nav-pill"
import { mainNavOf, type MainNav } from "@/shared/lib/main-nav"
import { paths } from "@/shared/lib/paths"
import { firstChar } from "@/shared/lib/utils"

/**
 * 그룹 밖 화면들의 위쪽 헤더(시안 3종의 "A · 카드 목록" 헤더): 로고, 스터디·정보공유·경험카드, 내 계정.
 * 오른쪽은 7~9차 시안의 제안대로 "로그아웃" 대신 내 계정 동그라미를 둔다(로그아웃은 내 정보 화면에 있다).
 * 지금 메뉴는 아래 라우트의 handle.mainNav로 정한다(기능의 routes.tsx가 붙인다).
 */
export function MainHeader() {
  const viewer = useViewer()
  const current: MainNav | undefined = useMatches()
    .map((match) => mainNavOf(match.handle))
    .findLast((nav) => nav !== undefined)

  return (
    <header className="bg-background">
      {/* 휴대폰(640px 아래)에서는 로고 글자를 숨겨 메뉴와 내 계정이 한 줄에 들어가게 한다 */}
      <div className="flex min-h-18 items-center gap-3 border-b border-border-subtle bg-surface px-6 py-2 sm:gap-5">
        <Link to={paths.home()} aria-label="리드미 홈" className="inline-flex flex-none items-center gap-2 text-base font-bold text-foreground">
          <LogoMark size={26} />
          <span className="hidden sm:inline">리드미</span>
        </Link>
        <nav aria-label="주요 메뉴" className="min-w-0">
          <ul className="flex flex-wrap items-center gap-1">
            <li>
              {/* 스터디: 내 그룹 목록(시안 7차의 제안) */}
              <Link to={paths.groups()} aria-current={current === "study" ? "page" : undefined} className={navPill(current === "study")}>
                스터디
              </Link>
            </li>
            <li>
              {/* shortcut: 정보공유는 화면이 없어 눌러도 아무 일이 없다, 시안이 나오면 Link로 바꾼다 */}
              <PlaceholderLink aria-current={current === "info" ? "page" : undefined} className={navPill(current === "info")}>
                정보공유
              </PlaceholderLink>
            </li>
            <li>
              <Link to={paths.cards()} aria-current={current === "cards" ? "page" : undefined} className={navPill(current === "cards")}>
                경험카드
              </Link>
            </li>
          </ul>
        </nav>
        <div className="flex min-w-0 flex-1 items-center justify-end gap-2">
          <Link to={paths.account()} aria-label="내 계정" className="flex-none rounded-full">
            <Avatar>{firstChar(viewer.name)}</Avatar>
          </Link>
        </div>
      </div>
    </header>
  )
}
