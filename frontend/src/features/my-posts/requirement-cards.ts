// 요건과 내 카드(최종 시안): 요건 아래 카드 줄을 자르는 규칙과 카드 미리 보기 창의 앞뒤 넘기기. 화면과 떨어진 순수 함수다.

/** 요건 아래에 처음 보이는 카드 수. 나머지는 "카드 N장 더 보기"로 편다 */
export const SHOWN_CARDS = 3

/** 보일 카드와 접혀 있는(펴면 더 보이는) 카드 수. 편 뒤에도 more는 그대로라 "접기"를 보일지 정하는 데 쓴다 */
export function shownCards<T>(cards: T[], expanded: boolean): { shown: T[]; more: number } {
  return { shown: expanded ? cards : cards.slice(0, SHOWN_CARDS), more: Math.max(0, cards.length - SHOWN_CARDS) }
}

/** 창 위의 자리 글: "카드 5장 중 2번째" */
export const cardPosition = (index: number, count: number): string => `카드 ${count}장 중 ${index + 1}번째`

/** 창에서 앞(-1)이나 뒤(1)로 넘긴 자리. 처음이나 끝을 넘으면 null(버튼을 끈다) */
export function stepCard(index: number, delta: -1 | 1, count: number): number | null {
  const next = index + delta
  return next >= 0 && next < count ? next : null
}

/** 창의 태그 목록에서 강조할 태그: 겹친 낱말과 같은 것(대소문자는 가리지 않는다) */
export function isOverlapTag(tag: string, overlap: string[]): boolean {
  const lower = tag.toLowerCase()
  return overlap.some((word) => word.toLowerCase() === lower)
}
