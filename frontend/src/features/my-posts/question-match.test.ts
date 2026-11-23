import { describe, expect, it } from "vitest"

import { addableQuestionCards, limitError, maxCards, textError, toLimit } from "./question-match"
import type { CardChoice, QuestionCard } from "./types"

const choice = (cardId: string): CardChoice => ({ cardId, title: `카드 ${cardId}`, experience: "" })
const picked = (cardId: string): QuestionCard => ({ ...choice(cardId), reason: null, emphasis: [] })

describe("maxCards", () => {
  it("2장, 글자 수가 500자 아래면 1장, 글자 수가 없으면 2장", () => {
    expect(maxCards(499)).toBe(1)
    expect(maxCards(500)).toBe(2)
    expect(maxCards(null)).toBe(2)
  })
})

describe("addableQuestionCards", () => {
  const choices = [choice("1"), choice("2"), choice("3")]

  it("그 문항에 없는 카드만", () => {
    expect(addableQuestionCards(choices, { limit: 800, cards: [picked("2")] }).map((c) => c.cardId)).toEqual(["1", "3"])
  })

  it("꽉 찼으면 없다(짧은 문항은 1장에서 찬다)", () => {
    expect(addableQuestionCards(choices, { limit: 800, cards: [picked("1"), picked("2")] })).toEqual([])
    expect(addableQuestionCards(choices, { limit: 300, cards: [picked("1")] })).toEqual([])
  })
})

describe("문항 고치기 창의 검사", () => {
  it("글자 수는 비우거나 1~10,000의 정수", () => {
    expect(toLimit(" ")).toBeNull()
    expect(toLimit("800")).toBe(800)
    for (const ok of ["", "1", "800", "10000"]) expect(limitError(ok)).toBeNull()
    for (const bad of ["0", "10001", "1.5", "-3", "1e3"]) expect(limitError(bad)).not.toBeNull()
  })

  it("문항 글은 빈칸만으로는 안 된다", () => {
    expect(textError("  ")).not.toBeNull()
    expect(textError("지원 동기")).toBeNull()
  })
})
