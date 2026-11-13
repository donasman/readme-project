import type { RouteObject } from "react-router"

/**
 * 로그인과 계정 화면들. 로그인하지 않은 사람도 보는 화면이라, 앱의 뿌리 틀 바로 아래(보는 사람을 읽기 전)에 붙는다.
 * 화면은 그 주소에 처음 들어갈 때 불러온다(lazy).
 */
export const authRoutes: RouteObject[] = [
  {
    path: "/login",
    lazy: async () => ({ Component: (await import("./pages/login-page")).LoginPage }),
  },
]
