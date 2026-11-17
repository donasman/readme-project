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

/** 이야기 칸의 이름(화면에 보이는 순서, 경험카드 문서 4.8). STAR 넷 사이와 뒤에 어려움·배운 점이 온다. */
export const STORY_PARTS: [keyof Star, string][] = [
  ["situation", "상황"],
  ["task", "과제"],
  ["obstacle", "어려움"],
  ["action", "행동"],
  ["result", "결과"],
  ["learning", "배운 점"],
]

/** 어려움과 배운 점은 선택이다. 자료에 없으면 비워 두고, 비었으면 상세와 확인에 보이지 않는다 */
export const isOptionalPart = (key: keyof Star): boolean => key === "obstacle" || key === "learning"

/** 같은 경험인지 가르는 열쇠: 앞뒤 공백과 대소문자를 무시한다(서버의 lower(btrim(experience))와 같다). 빈 글자면 경험이 없는 카드다 */
export const experienceKey = (name: string): string => name.trim().toLowerCase()

/** 목록의 한 덩어리: 한 경험의 장면 카드들, 또는 경험이 없는 카드들(experience가 빈 글자) */
export interface CardBlock<T> {
  experience: string
  cards: T[]
}

/**
 * 카드를 경험별로 묶는다. 같은 경험의 카드는 처음 나온 자리에 모으고, 경험이 없는 카드는 이어진 것끼리 한 덩어리다.
 * 그 밖의 순서는 받은 그대로다. 경험 이름은 처음 나온 카드의 것을 앞뒤 공백만 지워 쓴다.
 */
export function groupByExperience<T extends Pick<ExperienceCard, "experience">>(cards: T[]): CardBlock<T>[] {
  const blocks: CardBlock<T>[] = []
  const byKey = new Map<string, CardBlock<T>>()
  for (const card of cards) {
    const key = experienceKey(card.experience)
    const last = blocks.at(-1)
    if (!key && last && !last.experience) last.cards.push(card)
    else if (!key) blocks.push({ experience: "", cards: [card] })
    else if (byKey.has(key)) byKey.get(key)?.cards.push(card)
    else {
      const block = { experience: card.experience.trim(), cards: [card] }
      byKey.set(key, block)
      blocks.push(block)
    }
  }
  return blocks
}

/** 이미 쓴 경험 이름(고치기 화면의 고르기 목록). 같은 경험은 한 번만, 처음 나온 순서로 */
export function experienceNames(cards: Pick<ExperienceCard, "experience">[]): string[] {
  return groupByExperience(cards).flatMap((b) => (b.experience ? [b.experience] : []))
}

/** 카드 확인에서 이 카드가 같은 경험의 장면 몇 장 가운데 몇 번째인지. 경험이 없거나 그 경험의 카드가 이 한 장이면 null */
export function sceneNumber(cards: Pick<ExperienceCard, "experience">[], index: number): { nth: number; of: number } | null {
  const key = experienceKey(cards[index]?.experience ?? "")
  if (!key) return null
  const same = cards.flatMap((c, i) => (experienceKey(c.experience) === key ? [i] : []))
  return same.length < 2 ? null : { nth: same.indexOf(index) + 1, of: same.length }
}

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
export function startFrom<T extends { id: string }>(cards: T[], id: string | null): T[] {
  const i = cards.findIndex((c) => c.id === id)
  return i <= 0 ? cards : [...cards.slice(i), ...cards.slice(0, i)]
}

/** 주소의 태그가 지금 태그 목록에 없으면(지운 태그, 손으로 고친 주소) 거르지 않는다. 눌린 버튼이 없어 끌 수 없기 때문이다. */
export function knownTag(tag: string, tags: CardTag[]): string {
  return tags.some((t) => t.name === tag) ? tag : ""
}
