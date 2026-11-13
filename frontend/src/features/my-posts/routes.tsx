import type { RouteObject } from "react-router"

import { revalidateOnParams } from "@/shared/lib/router"

/**
 * 홈과 내 공고 화면들(시안 8차). 앱의 위쪽 헤더 틀 아래에 붙는다. 헤더의 메뉴는 어느 것도 고르지 않고,
 * 본문 맨 위의 "내 공간" 줄(홈, 내 공고, 지원 현황, 달력, 메일 확인함)이 지금 화면을 보인다.
 */
export const myPostsRoutes: RouteObject[] = [
  {
    path: "/",
    lazy: async () => {
      const { HomePage, homeLoader } = await import("./pages/home-page")
      return { loader: homeLoader, Component: HomePage }
    },
  },
  {
    path: "/my-posts",
    // 거르기(? 뒤)만 바뀌면 다시 읽지 않는다
    shouldRevalidate: revalidateOnParams(),
    lazy: async () => {
      const { MyPostsPage, myPostsLoader } = await import("./pages/my-posts-page")
      return { loader: myPostsLoader, Component: MyPostsPage }
    },
  },
  {
    path: "/my-posts/:postingId",
    lazy: async () => {
      const { MyPostPage, myPostLoader } = await import("./pages/my-post-page")
      return { loader: myPostLoader, Component: MyPostPage }
    },
  },
  {
    path: "/board",
    // 고른 공고(?posting=)만 바뀌면 다시 읽지 않는다
    shouldRevalidate: revalidateOnParams(),
    lazy: async () => {
      const { BoardPage, boardLoader } = await import("./pages/board-page")
      return { loader: boardLoader, Component: BoardPage }
    },
  },
]
