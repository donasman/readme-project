import { Link, useNavigate } from "react-router"

import { CheckIcon, ChevronDownIcon, MenuIcon, PlusIcon } from "@/shared/icons"
import { PlaceholderLink } from "@/shared/components/placeholder-link"
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
import type { Group } from "@/domain/group/types"
import type { Viewer } from "@/domain/session/types"
import { paths } from "@/shared/lib/paths"
import { cn } from "@/shared/lib/utils"

export type GroupSection = "posts" | "members"

interface GroupHeaderProps {
  viewer: Viewer
  groups: Group[]
  group: Group
  section: GroupSection
}

const navLink =
  "inline-flex min-h-control-sm items-center rounded-full px-4 text-sm leading-body font-bold whitespace-nowrap"

/**
 * 그룹 화면들의 머리. Arc app-header 가 스크롤됐을 때의 값(바깥 여백 12px 16px, radius-panel, shadow-floating)으로
 * 그렸다. Arc 헤더는 브랜드가 <a> 안이라 그룹 전환 버튼을 넣을 수 없어 따로 만들었다.
 */
export function GroupHeader({ viewer, groups, group, section }: GroupHeaderProps) {
  const navigate = useNavigate()
  const listPath = paths.groupPosts(group.id)

  return (
    <header className="relative z-20">
      <div className="mx-4 mt-3 flex min-h-18 items-center gap-2 rounded-panel border border-surface bg-surface px-4 py-2 shadow-floating md:gap-3 md:pr-4 md:pl-6 dark:border-float-edge">
        <Link
          to={paths.home()}
          aria-label="리드미 홈"
          className="inline-flex min-h-control-md flex-none items-center text-base leading-body font-bold text-foreground"
        >
          리드미
        </Link>

        <DropdownMenu>
          <DropdownMenuTrigger className="inline-flex h-control-md min-w-0 flex-none cursor-pointer items-center gap-2 rounded-full bg-surface-muted pr-3 pl-1.5 text-sm font-bold text-foreground">
            <span
              aria-hidden="true"
              className="grid size-8 flex-none place-items-center rounded-full bg-primary text-label-sm font-bold text-primary-foreground"
            >
              {group.mark}
            </span>
            <span className="max-w-40 truncate">{group.name}</span>
            <ChevronDownIcon aria-hidden="true" className="size-4 flex-none" />
          </DropdownMenuTrigger>
          <DropdownMenuContent className="min-w-66">
            <DropdownMenuGroup>
              <DropdownMenuLabel>내 그룹</DropdownMenuLabel>
              {groups.map((g) => (
                <DropdownMenuItem key={g.id} className="pl-2" onClick={() => navigate(paths.groupPosts(g.id))}>
                  <span
                    aria-hidden="true"
                    className="grid size-7 flex-none place-items-center rounded-full bg-surface-sunken text-xs font-bold"
                  >
                    {g.mark}
                  </span>
                  <span className="min-w-0 flex-1 whitespace-nowrap">{g.name}</span>
                  <span className="flex-none text-xs font-normal text-text-secondary">공고 {g.openPostCount}</span>
                  {g.id === group.id && <CheckIcon role="img" aria-label="지금 보는 그룹" className="text-primary" />}
                </DropdownMenuItem>
              ))}
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            {/* 아직 화면이 없다: 내 그룹 모두 보기, 그룹 만들기 */}
            <DropdownMenuItem>내 그룹 모두 보기</DropdownMenuItem>
            <DropdownMenuItem className="gap-2">
              <PlusIcon aria-hidden="true" />
              <span>그룹 만들기</span>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>

        <nav aria-label="그룹 메뉴" className="ml-2 hidden min-w-0 md:block">
          <ul className="flex items-center gap-1">
            <li>
              <Link
                to={listPath}
                aria-current={section === "posts" ? "page" : undefined}
                className={cn(
                  navLink,
                  section === "posts"
                    ? "bg-accent-subtle text-accent-strong"
                    : "text-text-secondary hover:bg-surface-muted hover:text-foreground"
                )}
              >
                공고
              </Link>
            </li>
            <li>
              <PlaceholderLink
                aria-current={section === "members" ? "page" : undefined}
                className={cn(
                  navLink,
                  section === "members"
                    ? "bg-accent-subtle text-accent-strong"
                    : "text-text-secondary hover:bg-surface-muted hover:text-foreground"
                )}
              >
                멤버
              </PlaceholderLink>
            </li>
          </ul>
        </nav>

        <div className="flex min-w-0 flex-1 items-center justify-end gap-2">
          <button
            type="button"
            aria-label="내 계정"
            className="inline-flex size-control-sm flex-none cursor-pointer items-center justify-center rounded-full bg-surface-sunken text-label-sm font-bold text-foreground"
          >
            {viewer.name.slice(0, 1)}
          </button>
          {/* 768px 미만에서만: 숨은 그룹 메뉴를 여기서 연다 */}
          <DropdownMenu>
            <DropdownMenuTrigger
              render={<Button variant="outline" size="icon-sm" aria-label="메뉴" className="md:hidden" />}
            >
              <MenuIcon aria-hidden="true" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => navigate(listPath)}>
                <span className="flex-1">공고</span>
                {section === "posts" && <CheckIcon role="img" aria-label="지금 보는 메뉴" className="text-primary" />}
              </DropdownMenuItem>
              <DropdownMenuItem>
                <span className="flex-1">멤버</span>
                {section === "members" && <CheckIcon role="img" aria-label="지금 보는 메뉴" className="text-primary" />}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </header>
  )
}
