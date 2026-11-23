// 경험카드 우선순위의 규칙(카드 우선순위 문서): 목록 크기, 자리 옮기기, 더할 수 있는 카드.
import type { CardChoice, PriorityCard } from "./types"

/** 목록에 두는 카드 수. 서버도 5장까지 받는다 */
export const MAX_PRIORITY = 5

/** index의 카드를 by칸 옮긴 새 목록. 끝을 넘으면 그대로 돌려준다. */
export function moveBy<T>(list: T[], index: number, by: number): T[] {
  const to = index + by
  if (index < 0 || index >= list.length || to < 0 || to >= list.length) return list
  const next = [...list]
  const [item] = next.splice(index, 1)
  next.splice(to, 0, item)
  return next
}

/** 목록에 없는 내 카드. 목록이 꽉 찼으면 없다. */
export function addableCards(choices: CardChoice[], list: PriorityCard[]): CardChoice[] {
  if (list.length >= MAX_PRIORITY) return []
  return choices.filter((c) => !list.some((p) => p.cardId === c.cardId))
}
