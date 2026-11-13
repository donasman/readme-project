import type { RouteObject } from "react-router"

import type { MainRouteHandle } from "@/shared/lib/main-nav"
import { revalidateOnParams } from "@/shared/lib/router"

const handle: MainRouteHandle = { mainNav: "cards" }

/**
 * 경험카드 화면들. 앱의 위쪽 헤더 틀(스터디·정보공유·경험카드) 아래에 붙는다.
 * 화면과 loader는 그 주소에 처음 들어갈 때 불러온다(lazy). 거르기(? 뒤)만 바뀌면 다시 읽지 않는다.
 */
export const cardsRoutes: RouteObject[] = [
  {
    path: "/cards",
    handle,
    shouldRevalidate: revalidateOnParams(),
    lazy: async () => {
      const { CardsPage, cardsLoader } = await import("./pages/cards-page")
      return { loader: cardsLoader, Component: CardsPage }
    },
  },
]
