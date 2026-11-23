import type { RouteObject } from "react-router"

import type { MainRouteHandle } from "@/shared/lib/main-nav"
import { revalidateOnParams } from "@/shared/lib/router"

const handle: MainRouteHandle = { mainNav: "home" }

/**
 * 홈과 내 공고 화면들(시안 8차). 앱의 위쪽 헤더 틀 아래에 붙고 헤더의 "대시보드"에 든다(11차 시안).
 * 본문 맨 위의 "내 공간" 줄(오늘, 내 공고, 지원 현황, 달력, 메일 확인함)이 지금 화면을 보인다.
 */
export const myPostsRoutes: RouteObject[] = [
  {
    handle,
    path: "/",
    lazy: async () => {
      const { HomePage, homeLoader } = await import("./pages/home-page")
      return { loader: homeLoader, Component: HomePage }
    },
  },
  {
    handle,
    path: "/my-posts",
    // 거르기(? 뒤)만 바뀌면 다시 읽지 않는다
    shouldRevalidate: revalidateOnParams(),
    lazy: async () => {
      const { MyPostsPage, myPostsLoader } = await import("./pages/my-posts-page")
      return { loader: myPostsLoader, Component: MyPostsPage }
    },
  },
  {
    // 그룹 없이 내 공고에 넣기(시안 10차). 읽은 결과는 칸 확인으로 넘긴다(주소에는 싣지 않는다)
    handle,
    path: "/my-posts/new",
    lazy: async () => ({ Component: (await import("./pages/my-post-new-page")).MyPostNewPage }),
  },
  {
    handle,
    path: "/my-posts/new/check",
    lazy: async () => ({ Component: (await import("./pages/my-post-check-page")).MyPostCheckPage }),
  },
  {
    handle,
    path: "/my-posts/:postingId",
    lazy: async () => {
      const { MyPostPage, myPostLoader } = await import("./pages/my-post-page")
      return { loader: myPostLoader, Component: MyPostPage }
    },
  },
  {
    handle,
    path: "/calendar",
    // 달(?month=)만 바뀌면 다시 읽지 않는다
    shouldRevalidate: revalidateOnParams(),
    lazy: async () => {
      const { CalendarPage, calendarLoader } = await import("./pages/calendar-page")
      return { loader: calendarLoader, Component: CalendarPage }
    },
  },
  {
    handle,
    path: "/board",
    // 고른 공고(?posting=)만 바뀌면 다시 읽지 않는다
    shouldRevalidate: revalidateOnParams(),
    lazy: async () => {
      const { BoardPage, boardLoader } = await import("./pages/board-page")
      return { loader: boardLoader, Component: BoardPage }
    },
  },
]
