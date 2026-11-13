import { useRouteLoaderData } from "react-router"

import { GROUP_ROUTE_ID, type groupLoader } from "./loader"

/** 지금 그룹과 내 그룹들. 그룹 라우트 아래 화면에서 쓴다. */
export function useGroup() {
  const data = useRouteLoaderData<typeof groupLoader>(GROUP_ROUTE_ID)
  if (!data) throw new Error("useGroup은 그룹 라우트(groupLoader) 아래에서만 쓴다.")
  return data
}
