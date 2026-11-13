import { NavLink, Outlet } from "react-router"

import { Heading } from "@/shared/components/heading"
import { paths } from "@/shared/lib/paths"
import { joinClasses } from "@/shared/lib/utils"

const item = "inline-flex min-h-control-sm items-center rounded-full px-4 text-sm font-bold whitespace-nowrap"

/** 설정의 틀(시안 9차 "내 정보"): 제목, 왼쪽 메뉴, 오른쪽 720px 본문. */
export function SettingsLayout() {
  const link = ({ isActive }: { isActive: boolean }) =>
    joinClasses(item, isActive ? "bg-accent-subtle text-accent-strong" : "text-text-secondary hover:bg-surface-muted hover:text-foreground")
  return (
    <main className="mx-auto flex max-w-[1120px] flex-col gap-6 px-6 pt-10 pb-16">
      <Heading level={1} size="text-3xl">
        설정
      </Heading>
      <div className="flex flex-wrap items-start gap-x-10 gap-y-6">
        <nav aria-label="설정" className="flex max-w-full min-w-0 flex-[1_1_180px] flex-col gap-1">
          <NavLink to={paths.account()} className={link}>
            내 정보
          </NavLink>
          <NavLink to={paths.mailSettings()} className={link}>
            메일 자동화
          </NavLink>
          <NavLink to={paths.withdraw()} className={link}>
            탈퇴
          </NavLink>
          <span className="inline-flex min-h-control-sm items-center rounded-full px-4 text-sm font-medium whitespace-nowrap text-text-secondary">외부 연동 (나중에)</span>
        </nav>
        <div className="flex max-w-[720px] min-w-0 flex-[999_1_560px] flex-col gap-6">
          <Outlet />
        </div>
      </div>
    </main>
  )
}
