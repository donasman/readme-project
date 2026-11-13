import { redirect, type RouteObject } from "react-router"

import { paths } from "@/shared/lib/paths"

/** 설정(시안 9차): 틀(제목과 왼쪽 메뉴) 아래 내 정보, 메일 자동화, 탈퇴. 헤더의 내 계정 동그라미가 내 정보로 온다. */
export const settingsRoutes: RouteObject[] = [
  {
    path: "/settings",
    lazy: async () => ({ Component: (await import("./components/settings-layout")).SettingsLayout }),
    children: [
      { index: true, loader: () => redirect(paths.account()), element: null },
      {
        path: "account",
        lazy: async () => {
          const { AccountPage, accountLoader } = await import("./pages/account-page")
          return { loader: accountLoader, Component: AccountPage }
        },
      },
      {
        path: "mail",
        lazy: async () => {
          const { MailSettingsPage, mailSettingsLoader } = await import("./pages/mail-settings-page")
          return { loader: mailSettingsLoader, Component: MailSettingsPage }
        },
      },
      {
        path: "withdraw",
        lazy: async () => {
          const { WithdrawPage, withdrawLoader } = await import("./pages/withdraw-page")
          return { loader: withdrawLoader, Component: WithdrawPage }
        },
      },
    ],
  },
]
