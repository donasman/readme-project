// 경험카드 목록의 거르기 규칙과 태그 순서. 화면과 떨어진 순수 함수다.
import type { CardTag, ExperienceCard } from "./types"

/** 목록 보기: 전체, 확인 필요(verified = false), 확인됨 */
export type CardStatus = "all" | "unverified" | "verified"

export interface CardFilters {
  status: CardStatus
  /** 고른 태그. 빈 글자면 거르지 않는다 */
  tag: string
}

export const DEFAULT_CARD_FILTERS: CardFilters = { status: "all", tag: "" }

export const CARD_STATUS_LABELS: Record<CardStatus, string> = {
  all: "전체",
  unverified: "확인 필요",
  verified: "확인됨",
}

export function matchesCardFilters(card: ExperienceCard, filters: CardFilters): boolean {
  if (filters.status === "unverified" && card.verified) return false
  if (filters.status === "verified" && !card.verified) return false
  if (filters.tag && !card.tags.includes(filters.tag)) return false
  return true
}

export function filterCards(cards: ExperienceCard[], filters: CardFilters): ExperienceCard[] {
  return cards.filter((card) => matchesCardFilters(card, filters))
}

/** 확인을 기다리는 카드 수. 거르기와 상관없이 센다 */
export function countUnverified(cards: ExperienceCard[]): number {
  return cards.filter((card) => !card.verified).length
}

/** 태그 거르기 버튼의 순서: 붙은 카드가 많은 순, 같으면 이름순 */
export function orderTags(tags: CardTag[]): CardTag[] {
  return [...tags].sort((a, b) => b.count - a.count || a.name.localeCompare(b.name, "en", { sensitivity: "base" }))
}

/** 카드 아래 줄: 어디서 · 맡은 일. 둘 다 없으면 빈 글자 */
export function orgAndRole(card: Pick<ExperienceCard, "org" | "role">): string {
  return [card.org, card.role].filter(Boolean).join(" · ")
}
