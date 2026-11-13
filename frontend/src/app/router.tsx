import { createBrowserRouter, redirect } from "react-router"

import { GroupLayout } from "@/app/layouts/group-layout"
import { RootFrame, RootLayout } from "@/app/layouts/root-layout"
import { RouteError } from "@/app/route-error"
import { getMyGroups } from "@/domain/group/api"
import { GROUP_ROUTE_ID, groupLoader } from "@/domain/group/loader"
import { SESSION_ROUTE_ID, sessionLoader } from "@/domain/session/loader"
import { groupPostsRoutes } from "@/features/group-posts"
import { DevComponentsPage } from "@/pages/dev-components-page"
import { paths } from "@/shared/lib/paths"
import { revalidateOnParams } from "@/shared/lib/router"

// 앱의 라우트. 틀(뿌리, 그룹)과 각 기능의 라우트 묶음을 조립만 한다.
export const router = createBrowserRouter([
  {
    // 뿌리: 보는 사람을 한 번 읽는다(useViewer).
    id: SESSION_ROUTE_ID,
    loader: sessionLoader,
    shouldRevalidate: revalidateOnParams(),
    element: <RootLayout />,
    // 뿌리의 오류 화면은 뿌리 element 대신 그려지므로 틀을 따로 씌운다.
    errorElement: (
      <RootFrame>
        <RouteError />
      </RootFrame>
    ),
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
          ...groupPostsRoutes,
        ],
      },
      // 부품 확인용 페이지(계획 4.9).
      { path: "/dev/components", element: <DevComponentsPage /> },
      { path: "*", element: <RouteError notFound /> },
    ],
  },
])
