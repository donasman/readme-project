import type { RouteObject } from "react-router"

import type { GroupRouteHandle } from "@/domain/group/sections"
import { revalidateOnParams } from "@/shared/lib/router"

const handle: GroupRouteHandle = { section: "posts" }

/**
 * 그룹 공고 화면들. 앱의 그룹 라우트(/groups/:groupId) 아래에 붙는다.
 * 화면과 loader는 그 주소에 처음 들어갈 때 불러온다(lazy). 첫 번들에는 들어가지 않는다.
 */
export const groupPostsRoutes: RouteObject[] = [
  {
    path: "posts",
    handle,
    children: [
      {
        index: true,
        shouldRevalidate: revalidateOnParams("groupId"),
        lazy: async () => {
          const { GroupPostsPage, groupPostsLoader } = await import("./pages/group-posts-page")
          return { loader: groupPostsLoader, Component: GroupPostsPage }
        },
      },
      {
        // 공고 올리기. 읽은 결과는 칸 확인으로 넘긴다(주소에는 싣지 않는다)
        path: "new",
        lazy: async () => ({ Component: (await import("./pages/post-new-page")).PostNewPage }),
      },
      {
        path: "new/check",
        lazy: async () => ({ Component: (await import("./pages/post-check-page")).PostCheckPage }),
      },
      {
        path: ":postId",
        lazy: async () => {
          const { GroupPostPage, groupPostLoader } = await import("./pages/group-post-page")
          return { loader: groupPostLoader, Component: GroupPostPage }
        },
      },
    ],
  },
]
