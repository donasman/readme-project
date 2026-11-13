import { createBrowserRouter, redirect, type RouteObject } from "react-router"

import { GroupLayout } from "@/app/layouts/group-layout"
import { MainLayout } from "@/app/layouts/main-layout"
import { RootFrame, RootLayout } from "@/app/layouts/root-layout"
import { RouteError } from "@/app/route-error"
import { GROUP_ROUTE_ID, groupLoader } from "@/domain/group/loader"
import { SESSION_ROUTE_ID, sessionLoader } from "@/domain/session/loader"
import { authRoutes } from "@/features/auth"
import { cardsRoutes } from "@/features/cards"
import { groupPostsRoutes } from "@/features/group-posts"
import { groupMembersRoutes, groupsRoutes } from "@/features/groups"
import { mailRoutes } from "@/features/mail"
import { myPostsRoutes } from "@/features/my-posts"
import { settingsRoutes } from "@/features/settings"
import { paths } from "@/shared/lib/paths"
import { revalidateOnParams } from "@/shared/lib/router"

/** 부품 확인용 페이지(계획 4.9). 개발 서버에서만 등록한다. 운영 빌드에서는 이 코드가 번들에서 빠진다. */
const devRoutes: RouteObject[] = import.meta.env.DEV
  ? [
      {
        path: "/dev/components",
        lazy: async () => ({ Component: (await import("@/app/dev/components-page")).DevComponentsPage }),
      },
    ]
  : []

// 앱의 라우트. 틀(뿌리, 그룹)과 각 기능의 라우트 묶음을 조립만 한다.
export const router = createBrowserRouter([
  {
    // 뿌리: 모든 화면의 틀. 로그인처럼 로그인 전에 보는 화면은 여기 바로 붙는다.
    element: <RootLayout />,
    // 첫 진입에서 화면 조각(lazy)과 loader를 기다리는 동안 보이는 빈 틀.
    hydrateFallbackElement: <RootFrame />,
    // 뿌리의 오류 화면은 뿌리 element 대신 그려지므로 틀을 따로 씌운다.
    errorElement: (
      <RootFrame>
        <RouteError />
      </RootFrame>
    ),
    children: [
      ...authRoutes,
      {
        // 로그인한 화면들: 보는 사람을 한 번 읽는다(useViewer).
        id: SESSION_ROUTE_ID,
        loader: sessionLoader,
        shouldRevalidate: revalidateOnParams(),
        children: [
          {
            // 그룹: 내 그룹들과 지금 그룹을 한 번 읽는다(useGroup).
            id: GROUP_ROUTE_ID,
            path: "/groups/:groupId",
            loader: groupLoader,
            shouldRevalidate: revalidateOnParams("groupId"),
            element: <GroupLayout />,
            errorElement: <RouteError />,
            children: [
              // 그룹 주소만 오면 공고 목록으로 보낸다.
              { index: true, loader: ({ params }) => redirect(paths.groupPosts(params.groupId ?? "")), element: null },
              ...groupPostsRoutes,
              ...groupMembersRoutes,
            ],
          },
          // 그룹 밖 화면: 위쪽 헤더(스터디·정보공유·경험카드) 아래에 붙는다.
          // 첫 화면(/)은 홈이다. 그룹이 없으면 홈의 loader가 내 그룹(빈 상태)으로 보낸다.
          { element: <MainLayout />, children: [...myPostsRoutes, ...mailRoutes, ...groupsRoutes, ...cardsRoutes, ...settingsRoutes] },
        ],
      },
      ...devRoutes,
      { path: "*", element: <RouteError notFound /> },
    ],
  },
])
