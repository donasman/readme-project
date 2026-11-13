import { createBrowserRouter, Outlet, redirect, ScrollRestoration } from "react-router"

import { RouteError } from "@/app/route-error"
import { GroupLayout } from "@/components/layout/group-layout"
import { getMyGroups } from "@/domain/group/api"
import { GROUP_ROUTE_ID, groupLoader } from "@/domain/group/loader"
import { SESSION_ROUTE_ID, sessionLoader } from "@/domain/session/loader"
import { GroupPostPage, groupPostLoader } from "@/features/group-posts/pages/group-post-page"
import { GroupPostsPage, groupPostsLoader } from "@/features/group-posts/pages/group-posts-page"
import { DevComponentsPage } from "@/pages/dev-components-page"
import { paths } from "@/shared/lib/paths"
import { revalidateOnParams } from "@/shared/lib/router"

function Root() {
  return (
    <>
      <Outlet />
      <ScrollRestoration />
    </>
  )
}

export const router = createBrowserRouter([
  {
    // 뿌리: 보는 사람을 한 번 읽는다(useViewer).
    id: SESSION_ROUTE_ID,
    loader: sessionLoader,
    shouldRevalidate: revalidateOnParams(),
    element: <Root />,
    errorElement: <RouteError />,
    children: [
      {
        // 첫 화면: 내 첫 그룹의 공고 목록으로 보낸다.
        path: "/",
        loader: async () => {
          const groups = await getMyGroups()
          return redirect(groups.length ? paths.groupPosts(groups[0].id) : paths.devComponents())
        },
      },
      {
        // 그룹: 내 그룹들과 지금 그룹을 한 번 읽는다(useGroup).
        id: GROUP_ROUTE_ID,
        path: "/groups/:groupId",
        loader: groupLoader,
        shouldRevalidate: revalidateOnParams("groupId"),
        element: <GroupLayout />,
        errorElement: <RouteError />,
        children: [
          { index: true, loader: ({ params }) => redirect(paths.groupPosts(params.groupId ?? "")) },
          {
            path: "posts",
            loader: groupPostsLoader,
            shouldRevalidate: revalidateOnParams("groupId"),
            element: <GroupPostsPage />,
          },
          { path: "posts/:postId", loader: groupPostLoader, element: <GroupPostPage /> },
        ],
      },
      // 부품 확인용 페이지(계획 4.9).
      { path: "/dev/components", element: <DevComponentsPage /> },
      { path: "*", element: <RouteError notFound /> },
    ],
  },
])
