import type { RouteObject } from "react-router"

import type { MainRouteHandle } from "@/shared/lib/main-nav"

/** 메일 확인함(시안 9차). 앱의 위쪽 헤더 틀 아래에 붙고 헤더의 "홈"에 든다. 본문 맨 위 "내 공간" 줄이 지금 화면을 보인다. */
export const mailRoutes: RouteObject[] = [
  {
    path: "/mail",
    handle: { mainNav: "home" } satisfies MainRouteHandle,
    lazy: async () => {
      const { MailPage, mailLoader } = await import("./pages/mail-page")
      return { loader: mailLoader, Component: MailPage }
    },
  },
]
