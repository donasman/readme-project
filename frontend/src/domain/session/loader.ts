import { redirect } from "react-router"

import { paths } from "@/shared/lib/paths"

import { getViewer } from "./api"

/** 뿌리 라우트의 id. useViewer()가 이 id로 loader 값을 읽는다. */
export const SESSION_ROUTE_ID = "session"

/** 로그인한 화면들의 loader: 보는 사람을 한 번 읽는다. 로그인하지 않았으면 로그인 화면으로 보낸다. */
export async function sessionLoader() {
  const viewer = await getViewer()
  if (!viewer) throw redirect(paths.login())
  return viewer
}
