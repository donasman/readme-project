import { getViewer } from "./api"

/** 뿌리 라우트의 id. useViewer()가 이 id로 loader 값을 읽는다. */
export const SESSION_ROUTE_ID = "session"

/** 뿌리 라우트의 loader: 보는 사람을 한 번 읽는다. */
export function sessionLoader() {
  return getViewer()
}
