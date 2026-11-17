import type { RouteObject } from "react-router"

import type { MainRouteHandle } from "@/shared/lib/main-nav"
import { revalidateOnParams } from "@/shared/lib/router"

const handle: MainRouteHandle = { mainNav: "cards" }

/**
 * 경험카드 화면들. 앱의 위쪽 헤더 틀(홈·내 그룹·경험카드·정보공유) 아래에 붙는다.
 * 화면과 loader는 그 주소에 처음 들어갈 때 불러온다(lazy). 목록은 거르기(? 뒤)만 바뀌면 다시 읽지 않는다.
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
  {
    path: "/cards/sources",
    handle,
    lazy: async () => {
      const { CardSourcesPage, cardSourcesLoader } = await import("./pages/card-sources-page")
      return { loader: cardSourcesLoader, Component: CardSourcesPage }
    },
  },
  {
    path: "/cards/review",
    handle,
    // 확인하는 동안(카드를 고쳐도) 순서를 처음 받은 그대로 둔다
    shouldRevalidate: () => false,
    lazy: async () => {
      const { CardReviewPage, cardReviewLoader } = await import("./pages/card-review-page")
      return { loader: cardReviewLoader, Component: CardReviewPage }
    },
  },
  {
    path: "/cards/:cardId",
    handle,
    lazy: async () => {
      const { CardPage, cardLoader } = await import("./pages/card-page")
      return { loader: cardLoader, Component: CardPage }
    },
  },
  {
    // 직접 만들기: 고치기와 같은 화면을 빈 카드로 연다
    path: "/cards/new",
    handle,
    lazy: async () => {
      const { CardEditPage, cardNewLoader } = await import("./pages/card-edit-page")
      return { loader: cardNewLoader, Component: CardEditPage }
    },
  },
  {
    path: "/cards/:cardId/edit",
    handle,
    lazy: async () => {
      const { CardEditPage, cardEditLoader } = await import("./pages/card-edit-page")
      return { loader: cardEditLoader, Component: CardEditPage }
    },
  },
]
