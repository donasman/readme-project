// 경험카드 목록의 거르기 규칙, 태그 순서, 확인 순서. 화면과 떨어진 순수 함수다.
import type { CardTag, ExperienceCard, Star } from "./types"

/** 목록 보기: 전체, 확인 필요(verified = false), 확인됨 */
export type CardStatus = "all" | "unverified" | "verified"

export interface CardFilters {
  status: CardStatus
  /** 고른 태그. 빈 글자면 거르지 않는다 */
  tag: string
}

export const DEFAULT_CARD_FILTERS: CardFilters = { status: "all", tag: "" }

/** STAR 네 칸의 이름(화면에 보이는 순서). */
export const STAR_PARTS: [keyof Star, string][] = [
  ["situation", "상황"],
  ["task", "과제"],
  ["action", "행동"],
  ["result", "결과"],
]

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

/** 카드 확인의 순서: id인 카드부터 보고, 그 앞 카드들은 뒤로 돌린다. 없는 id면 그대로 둔다. */
export function startFrom<T extends { id: number }>(cards: T[], id: number): T[] {
  const i = cards.findIndex((c) => c.id === id)
  return i <= 0 ? cards : [...cards.slice(i), ...cards.slice(0, i)]
}
