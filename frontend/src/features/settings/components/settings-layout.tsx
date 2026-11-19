import { NavLink, Outlet } from "react-router"

import { Heading } from "@/shared/components/heading"
import { navPill } from "@/shared/components/nav-pill"
import { Page } from "@/shared/components/page"
import { mainColumn } from "@/shared/components/page-layout"
import { paths } from "@/shared/lib/paths"
import { joinClasses } from "@/shared/lib/utils"

/** 설정의 틀(시안 9차 "내 정보"): 제목, 왼쪽 메뉴, 오른쪽 720px 본문. */
export function SettingsLayout() {
  const link = ({ isActive }: { isActive: boolean }) => navPill(isActive)
  return (
    <Page narrow className="gap-6 pt-10">
      <Heading level={1} size="text-3xl">
        설정
      </Heading>
      <div className="flex flex-wrap items-start gap-x-10 gap-y-6">
        <nav aria-label="설정" className="flex max-w-full min-w-0 flex-[1_1_180px] flex-col gap-1">
          <NavLink to={paths.account()} className={link}>
            내 정보
          </NavLink>
          <NavLink to={paths.profile()} className={link}>
            기본 프로필
          </NavLink>
          <NavLink to={paths.grades()} className={link}>
            학점 계산기
          </NavLink>
          <NavLink to={paths.mailSettings()} className={link}>
            메일 자동화
          </NavLink>
          <NavLink to={paths.withdraw()} className={link}>
            탈퇴
          </NavLink>
          <span className="inline-flex min-h-control-sm items-center rounded-full px-4 text-sm font-medium whitespace-nowrap text-text-secondary">외부 연동 (나중에)</span>
        </nav>
        <div className={joinClasses(mainColumn, "max-w-[720px] gap-6")}>
          <Outlet />
        </div>
      </div>
    </Page>
  )
}
