import { useCallback, useMemo } from "react"
import { useSearchParams } from "react-router"

import { DEFAULT_CARD_FILTERS, type CardFilters, type CardStatus } from "./card-rules"

const STATUSES: CardStatus[] = ["all", "unverified", "verified"]

/** 거르기 값을 주소의 ? 뒤 글자로. 기본값은 적지 않는다(?status=unverified&tag=Java). */
export function cardFiltersSearch(filters: CardFilters): string {
  const out = new URLSearchParams()
  if (filters.status !== DEFAULT_CARD_FILTERS.status) out.set("status", filters.status)
  if (filters.tag) out.set("tag", filters.tag)
  const search = out.toString()
  return search ? `?${search}` : ""
}

/** 경험카드 목록의 거르기를 주소의 ? 뒤에 둔다. 새로 고치거나 링크를 나눠도 고른 거르기가 남는다. */
export function useCardFilters(): [CardFilters, (patch: Partial<CardFilters>) => void] {
  const [params, setParams] = useSearchParams()

  const filters = useMemo<CardFilters>(() => {
    const status = params.get("status") as CardStatus | null
    return {
      status: status && STATUSES.includes(status) ? status : DEFAULT_CARD_FILTERS.status,
      tag: params.get("tag") ?? "",
    }
  }, [params])

  const update = useCallback(
    (patch: Partial<CardFilters>) => {
      setParams(new URLSearchParams(cardFiltersSearch({ ...filters, ...patch })), { replace: true, preventScrollReset: true })
    },
    [filters, setParams]
  )

  return [filters, update]
}
