import { describe, expect, it } from "vitest"

import { cardPosition, shownCards, stepCard } from "./requirement-cards"

describe("요건 아래 카드 줄", () => {
  it("셋까지 보이고 나머지 수를 센다", () => {
    expect(shownCards([1, 2, 3, 4, 5], false)).toEqual({ shown: [1, 2, 3], more: 2 })
    expect(shownCards([1, 2, 3, 4, 5], true)).toEqual({ shown: [1, 2, 3, 4, 5], more: 2 })
  })
  it("셋 이하면 모두 보이고 더 보기가 없다", () => {
    expect(shownCards([1, 2, 3], false)).toEqual({ shown: [1, 2, 3], more: 0 })
    expect(shownCards([], false)).toEqual({ shown: [], more: 0 })
  })
})

describe("카드 미리 보기 창", () => {
  it("자리 글", () => {
    expect(cardPosition(0, 5)).toBe("카드 5장 중 1번째")
    expect(cardPosition(4, 5)).toBe("카드 5장 중 5번째")
  })
  it("앞뒤로 넘기되 처음과 끝을 넘지 않는다", () => {
    expect(stepCard(1, -1, 3)).toBe(0)
    expect(stepCard(1, 1, 3)).toBe(2)
    expect(stepCard(0, -1, 3)).toBeNull()
    expect(stepCard(2, 1, 3)).toBeNull()
    expect(stepCard(0, 1, 1)).toBeNull()
  })
})
