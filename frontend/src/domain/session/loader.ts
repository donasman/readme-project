import { redirect, type LoaderFunctionArgs } from "react-router"

import { paths } from "@/shared/lib/paths"

import { getViewer } from "./api"
import { rememberReturnTo, takeReturnTo } from "./return-to"

/** 뿌리 라우트의 id. useViewer()가 이 id로 loader 값을 읽는다. */
export const SESSION_ROUTE_ID = "session"

/**
 * 로그인한 화면들의 loader: 보는 사람을 한 번 읽는다. 로그인하지 않았으면 첫 화면(/)은 랜딩으로,
 * 그 밖의 화면은 그 화면을 돌아갈 화면으로 두고 로그인 화면으로 보낸다. 돌아갈 화면이 있으면 그리로 보낸다(rememberReturnTo).
 */
export async function sessionLoader({ request }: LoaderFunctionArgs) {
  const viewer = await getViewer()
  if (!viewer) {
    const { pathname, search } = new URL(request.url)
    if (pathname === paths.home()) throw redirect(paths.welcome())
    // 공유받은 링크(그룹 공고 등)로 왔으면 로그인한 뒤 그 화면으로 돌아간다(로그인 문서 4.1)
    rememberReturnTo(pathname + search)
    throw redirect(paths.login())
  }
  const back = takeReturnTo()
  if (back) throw redirect(back)
  return viewer
}
