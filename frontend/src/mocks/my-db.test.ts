import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { deleteCard, listCards } from "./cards-db"
import { draftCardPriority, matchQuestions, saveCardPriority, saveQuestions, setQuestionCards } from "./my-db"

// 새로 만들 때만 기다린다(600ms). 다시 쓰는 답은 기다리지 않는다
async function run<T>(call: () => Promise<T>): Promise<T> {
  const done = call()
  await vi.runAllTimersAsync()
  return done
}

beforeEach(() => vi.useFakeTimers())
afterEach(() => vi.useRealTimers())

// 서버처럼 입력이 같으면 LLM을 다시 부르지 않고 앞 답을 다시 준다. 가짜 서버의 값은 모듈에 남으므로 공고를 나눠 쓴다
describe("LLM 답 다시 쓰기", () => {
  it("초안: 입력이 같으면 손으로 고친 순서를 앞 초안으로 되돌리고, 카드가 바뀌면 새로 만든다", async () => {
    const first = await run(() => draftCardPriority("p-pub1"))
    expect(first).toMatchObject({ ok: true, reused: false })
    if (!first.ok) return
    saveCardPriority("p-pub1", [first.cards[1].cardId])
    expect(await draftCardPriority("p-pub1")).toEqual({ ...first, reused: true })

    deleteCard(listCards().at(-1)?.id ?? "")
    expect(await run(() => draftCardPriority("p-pub1"))).toMatchObject({ ok: true, reused: false })
  })

  it("문항 매칭: 입력이 같으면 앞 결과로 되돌리고, 문항 글이 바뀌면 새로 고른다", async () => {
    const first = await run(() => matchQuestions("p-be1"))
    expect(first).toMatchObject({ ok: true, reused: false })
    if (!first.ok) return
    const picked = first.questions.find((q) => q.cards.length > 0)
    expect(picked).toBeDefined()
    setQuestionCards("p-be1", picked?.id ?? "", [])
    expect(await matchQuestions("p-be1")).toEqual({ ...first, reused: true })

    saveQuestions("p-be1", [{ ...first.questions[0], text: `${first.questions[0].text} 고침` }, ...first.questions.slice(1)])
    expect(await run(() => matchQuestions("p-be1"))).toMatchObject({ ok: true, reused: false })
  })
})
