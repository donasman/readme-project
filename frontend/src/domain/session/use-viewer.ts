import { useRouteLoaderData } from "react-router"

import { SESSION_ROUTE_ID, type sessionLoader } from "./loader"

/** 보는 사람. 뿌리 라우트 아래 어느 화면에서나 쓴다. */
export function useViewer() {
  const viewer = useRouteLoaderData<typeof sessionLoader>(SESSION_ROUTE_ID)
  if (!viewer) throw new Error("useViewer는 뿌리 라우트(sessionLoader) 아래에서만 쓴다.")
  return viewer
}
