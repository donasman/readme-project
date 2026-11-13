import type { RouteObject } from "react-router"

/** 메일 확인함(시안 9차). 앱의 위쪽 헤더 틀 아래에 붙고, 본문 맨 위 "내 공간" 줄이 지금 화면을 보인다. */
export const mailRoutes: RouteObject[] = [
  {
    path: "/mail",
    lazy: async () => {
      const { MailPage, mailLoader } = await import("./pages/mail-page")
      return { loader: mailLoader, Component: MailPage }
    },
  },
]
