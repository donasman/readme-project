import { useCallback, useMemo } from "react"
import { useSearchParams } from "react-router"

import { DECISIONS, DEFAULT_MY_FILTERS, type MyFilters } from "./status"

/** 내 공고 거르기를 주소의 ? 뒤에 둔다(?view=closed&decision=hold&q=물류). 기본값은 적지 않는다. */
export function useMyFilters(): [MyFilters, (patch: Partial<MyFilters>) => void] {
  const [params, setParams] = useSearchParams()

  const filters = useMemo<MyFilters>(() => {
    const decision = params.get("decision")
    return {
      view: params.get("view") === "closed" ? "closed" : "open",
      decision: DECISIONS.find((d) => d === decision) ?? "all",
      query: params.get("q") ?? "",
    }
  }, [params])

  const update = useCallback(
    (patch: Partial<MyFilters>) => {
      const next = { ...filters, ...patch }
      const out = new URLSearchParams()
      if (next.view !== DEFAULT_MY_FILTERS.view) out.set("view", next.view)
      if (next.decision !== DEFAULT_MY_FILTERS.decision) out.set("decision", next.decision)
      if (next.query) out.set("q", next.query)
      setParams(out, { replace: true, preventScrollReset: true })
    },
    [filters, setParams]
  )

  return [filters, update]
}
