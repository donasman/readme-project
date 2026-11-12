import { createBrowserRouter, Outlet, redirect, ScrollRestoration } from "react-router"

import { RouteError } from "@/app/route-error"
import { GroupLayout, groupLoader, shouldRevalidateGroup } from "@/components/layout/group-layout"
import { getMyGroups } from "@/features/group-posts/api"
import { GroupPostPage, groupPostLoader } from "@/features/group-posts/pages/group-post-page"
import {
  GroupPostsPage,
  groupPostsLoader,
  shouldRevalidateGroupPosts,
} from "@/features/group-posts/pages/group-posts-page"
import { DevComponentsPage } from "@/pages/dev-components-page"

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
    element: <Root />,
    errorElement: <RouteError />,
    children: [
      {
        // 첫 화면: 내 첫 그룹의 공고 목록으로 보낸다.
        path: "/",
        loader: async () => {
          const { groups } = await getMyGroups()
          return redirect(groups.length ? `/groups/${groups[0].id}/posts` : "/dev/components")
        },
      },
      {
        id: "group",
        path: "/groups/:groupId",
        loader: groupLoader,
        shouldRevalidate: shouldRevalidateGroup,
        element: <GroupLayout />,
        errorElement: <RouteError />,
        children: [
          { index: true, loader: ({ params }) => redirect(`/groups/${params.groupId}/posts`) },
          {
            path: "posts",
            loader: groupPostsLoader,
            shouldRevalidate: shouldRevalidateGroupPosts,
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
