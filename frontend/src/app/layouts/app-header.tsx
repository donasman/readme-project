import { Link, useMatches, useNavigate, useRouteLoaderData } from "react-router"

import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { GroupMark } from "@/domain/group/group-mark"
import { GROUP_ROUTE_ID, type groupLoader } from "@/domain/group/loader"
import { useViewer } from "@/domain/session/use-viewer"
import type { GroupsPageState } from "@/features/groups"
import { Avatar } from "@/shared/components/avatar"
import { LogoMark } from "@/shared/components/logo-mark"
import { navPill } from "@/shared/components/nav-pill"
import { pageWidth } from "@/shared/components/page"
import { CheckIcon, ChevronDownIcon, MenuIcon, PlusIcon } from "@/shared/icons"
import { mainNavOf, type MainNav } from "@/shared/lib/main-nav"
import { paths } from "@/shared/lib/paths"
import { firstChar, joinClasses } from "@/shared/lib/utils"

const onMark = (on: boolean) => (on ? <CheckIcon role="img" aria-label="지금 보는 메뉴" className="text-primary" /> : null)

/**
 * 로그인한 모든 화면의 위쪽 헤더(11차 시안): 로고, 대시보드 · 내 그룹 · 경험카드 · 정보공유, 내 계정. 폭은 아래 본문과 같다(그룹 화면 1240px,
 * 그룹 밖 narrow 1120px). 로고와 내 계정이 본문의 양 끝에 세로로 맞는다.
 * 그룹 화면에서는 "내 그룹" 자리가 지금 그룹을 보이고 눌러서 그룹을 바꾼다. 그룹 밖에서는 내 그룹 화면으로 가는 링크다
 * (내 그룹 목록은 그룹 라우트만 읽는다). 768px 아래에서는 내 그룹 자리만 두고 나머지와 내 계정은 메뉴(☰)에 넣는다.
 * 지금 메뉴는 아래 라우트의 handle.mainNav로 정하고, 그룹 화면이면 내 그룹이다.
 */
export function AppHeader({ narrow = false }: { narrow?: boolean }) {
  const viewer = useViewer()
  const navigate = useNavigate()
  const groupData = useRouteLoaderData<typeof groupLoader>(GROUP_ROUTE_ID)
  const routeNav = useMatches()
    .map((match) => mainNavOf(match.handle))
    .findLast((nav) => nav !== undefined)
  const current: MainNav | undefined = groupData ? "groups" : routeNav
  const createState: GroupsPageState = { create: true }

  return (
    <header className="border-b border-border-subtle bg-surface">
      <div className={joinClasses("mx-auto flex min-h-18 items-center gap-3 md:gap-5", pageWidth(narrow))}>
        <Link to={paths.home()} aria-label="리드미 홈" className="inline-flex min-h-control-md flex-none items-center gap-2 text-base font-bold text-foreground">
          <LogoMark size={26} />
          <span className="hidden sm:inline">리드미</span>
        </Link>

        <nav aria-label="주요 메뉴" className="min-w-0 flex-1">
          <ul className="flex items-center gap-1">
            <li className="hidden md:block">
              <Link to={paths.home()} aria-current={current === "home" ? "page" : undefined} className={navPill(current === "home")}>
                대시보드
              </Link>
            </li>
            <li className="min-w-0">
              {groupData ? (
                <DropdownMenu>
                  <DropdownMenuTrigger
                    aria-label={`내 그룹: ${groupData.group.name}`}
                    className="inline-flex min-h-control-sm min-w-0 cursor-pointer items-center gap-2 rounded-full bg-accent-subtle pr-3 pl-1.5 text-sm font-bold whitespace-nowrap text-accent-strong"
                  >
                    <GroupMark group={groupData.group} size={24} />
                    <span className="truncate">{groupData.group.name}</span>
                    <ChevronDownIcon aria-hidden="true" className="size-4 flex-none" />
                  </DropdownMenuTrigger>
                  <DropdownMenuContent className="min-w-66">
                    <DropdownMenuGroup>
                      <DropdownMenuLabel>내 그룹</DropdownMenuLabel>
                      {groupData.groups.map((g) => (
                        <DropdownMenuItem key={g.id} className="pl-2" onClick={() => navigate(paths.groupPosts(g.id))}>
                          <GroupMark group={g} size={28} />
                          <span className="min-w-0 flex-1 whitespace-nowrap">{g.name}</span>
                          <span className="flex-none text-xs font-normal text-text-secondary">공고 {g.openPostCount}</span>
                          {g.id === groupData.group.id && <CheckIcon role="img" aria-label="지금 보는 그룹" className="text-primary" />}
                        </DropdownMenuItem>
                      ))}
                    </DropdownMenuGroup>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem onClick={() => navigate(paths.groups())}>내 그룹 모두 보기</DropdownMenuItem>
                    <DropdownMenuItem className="gap-2" onClick={() => navigate(paths.groups(), { state: createState })}>
                      <PlusIcon aria-hidden="true" />
                      <span>그룹 만들기</span>
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : (
                <Link to={paths.groups()} aria-current={current === "groups" ? "page" : undefined} className={navPill(current === "groups")}>
                  내 그룹
                </Link>
              )}
            </li>
            <li className="hidden md:block">
              <Link to={paths.cards()} aria-current={current === "cards" ? "page" : undefined} className={navPill(current === "cards")}>
                경험카드
              </Link>
            </li>
            <li className="hidden md:block">
              {/* shortcut: 정보공유는 아직 화면이 없어 누를 수 없게 흐리고 "준비 중"을 붙인다, 시안이 나오면 Link로 바꾼다 */}
              <span className="inline-flex min-h-control-sm items-center gap-1.5 rounded-full px-4 text-sm font-bold whitespace-nowrap text-text-secondary opacity-60">
                정보공유
                <span className="rounded-full bg-surface-muted px-1.5 text-[11px] leading-[18px] font-medium">준비 중</span>
              </span>
            </li>
          </ul>
        </nav>

        <Link to={paths.account()} aria-label="내 계정" className="hidden flex-none rounded-full md:block">
          {/* 바탕과 1.2:1이라 테두리로 버튼임을 보인다(3:1) */}
          <Avatar className="ring-1 ring-text-secondary/80">{firstChar(viewer.name)}</Avatar>
        </Link>
        <DropdownMenu>
          <DropdownMenuTrigger render={<Button variant="outline" size="icon-sm" aria-label="메뉴" className="md:hidden" />}>
            <MenuIcon aria-hidden="true" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="min-w-48">
            <DropdownMenuItem onClick={() => navigate(paths.home())}>
              <span className="flex-1">대시보드</span>
              {onMark(current === "home")}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => navigate(paths.cards())}>
              <span className="flex-1">경험카드</span>
              {onMark(current === "cards")}
            </DropdownMenuItem>
            <DropdownMenuItem disabled>
              <span className="flex-1">정보공유</span>
              <span className="text-xs font-normal">준비 중</span>
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={() => navigate(paths.account())}>내 계정</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}
