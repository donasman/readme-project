import { describe, expect, it } from "vitest"

import { addableCards, moveBy } from "./card-priority"
import type { PriorityCard } from "./types"

const card = (cardId: string): PriorityCard => ({ cardId, title: `카드 ${cardId}`, experience: "", reason: null })

describe("moveBy", () => {
  it("한 칸 위·아래로 옮긴다", () => {
    expect(moveBy(["a", "b", "c"], 2, -1)).toEqual(["a", "c", "b"])
    expect(moveBy(["a", "b", "c"], 0, 1)).toEqual(["b", "a", "c"])
  })

  it("끝을 넘으면 같은 목록이다", () => {
    const list = ["a", "b"]
    expect(moveBy(list, 0, -1)).toBe(list)
    expect(moveBy(list, 1, 1)).toBe(list)
    expect(moveBy([], 0, 1)).toEqual([])
  })
})

describe("addableCards", () => {
  const choices = ["1", "2", "3", "4", "5", "6"].map((id) => ({ cardId: id, title: `카드 ${id}`, experience: "" }))

  it("목록에 없는 카드만 돌려준다", () => {
    expect(addableCards(choices, [card("2"), card("5")]).map((c) => c.cardId)).toEqual(["1", "3", "4", "6"])
  })

  it("5장이 차면 없다", () => {
    expect(addableCards(choices, ["1", "2", "3", "4", "5"].map(card))).toEqual([])
  })
})
