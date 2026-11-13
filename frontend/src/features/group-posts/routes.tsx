import type { RouteObject } from "react-router"

import type { GroupRouteHandle } from "@/domain/group/sections"
import { revalidateOnParams } from "@/shared/lib/router"

import { GroupPostPage, groupPostLoader } from "./pages/group-post-page"
import { GroupPostsPage, groupPostsLoader } from "./pages/group-posts-page"

const handle: GroupRouteHandle = { section: "posts" }

/** 그룹 공고 화면들. 앱의 그룹 라우트(/groups/:groupId) 아래에 붙는다. */
export const groupPostsRoutes: RouteObject[] = [
  {
    path: "posts",
    handle,
    children: [
      {
        index: true,
        loader: groupPostsLoader,
        shouldRevalidate: revalidateOnParams("groupId"),
        element: <GroupPostsPage />,
      },
      { path: ":postId", loader: groupPostLoader, element: <GroupPostPage /> },
    ],
  },
]
