import { useCallback, useMemo } from "react"
import { useSearchParams } from "react-router"

import { DEFAULT_FILTERS, type ListFilters, type Sort } from "./list-rules"

const SORTS: Sort[] = ["due", "recent", "fans"]

/**
 * 목록 필터를 주소의 ? 뒤에 둔다(?tab=closed&sort=recent&member=garam&mine=1&q=…).
 * 상세에서 돌아와도 고른 필터가 남고, 기본값은 주소에 적지 않는다.
 */
export function useListFilters(): [ListFilters, (patch: Partial<ListFilters>) => void] {
  const [params, setParams] = useSearchParams()

  const filters = useMemo<ListFilters>(() => {
    const sort = params.get("sort") as Sort | null
    return {
      tab: params.get("tab") === "closed" ? "closed" : "open",
      sort: sort && SORTS.includes(sort) ? sort : DEFAULT_FILTERS.sort,
      member: params.get("member") ?? "",
      mine: params.get("mine") === "1",
      query: params.get("q") ?? "",
    }
  }, [params])

  const update = useCallback(
    (patch: Partial<ListFilters>) => {
      const next = { ...filters, ...patch }
      const out = new URLSearchParams()
      if (next.tab !== DEFAULT_FILTERS.tab) out.set("tab", next.tab)
      if (next.sort !== DEFAULT_FILTERS.sort) out.set("sort", next.sort)
      if (next.member) out.set("member", next.member)
      if (next.mine) out.set("mine", "1")
      if (next.query) out.set("q", next.query)
      setParams(out, { replace: true, preventScrollReset: true })
    },
    [filters, setParams]
  )

  return [filters, update]
}
