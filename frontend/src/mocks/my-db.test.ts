import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { deleteCard, getCard, listCards, updateCard, verifyCard } from "./cards-db"
import { addMyPosting, draftCardPriority, matchQuestions, saveCardPriority, saveQuestions, setQuestionCards } from "./my-db"

// 새로 만들 때만 기다린다(600ms). 다시 쓰는 답은 기다리지 않는다
async function run<T>(call: () => Promise<T>): Promise<T> {
  const done = call()
  await vi.runAllTimersAsync()
  return done
}

beforeEach(() => vi.useFakeTimers())
afterEach(() => vi.useRealTimers())

// 서버처럼 입력이 같으면 LLM을 다시 부르지 않고 앞 답을 다시 준다. 가짜 서버의 값은 모듈에 남으므로 공고를 나눠 쓰고, 카드를 바꾸는 테스트는 뒤에 둔다
describe("LLM 답 다시 쓰기", () => {
  it("문항 매칭: 입력이 같으면 앞 결과로 되돌리고, 글의 앞뒤 빈칸은 같은 입력, 문항 글이 바뀌면 새로 고른다", async () => {
    const first = await run(() => matchQuestions("p-be1"))
    expect(first).toMatchObject({ ok: true, reused: false })
    if (!first.ok) return
    const picked = first.questions.find((q) => q.cards.length > 0)
    expect(picked).toBeDefined()
    setQuestionCards("p-be1", picked?.id ?? "", [])
    expect(await matchQuestions("p-be1")).toEqual({ ...first, reused: true })

    const [q1, ...rest] = first.questions
    saveQuestions("p-be1", [{ ...q1, text: ` ${q1.text} ` }, ...rest])
    expect(await matchQuestions("p-be1")).toMatchObject({ ok: true, reused: true })

    saveQuestions("p-be1", [{ ...q1, text: `${q1.text} 고침` }, ...rest])
    expect(await run(() => matchQuestions("p-be1"))).toMatchObject({ ok: true, reused: false })
  })

  it("고른 카드가 없으면 기억하지 않아 다시 누르면 새로 만든다", async () => {
    const added = addMyPosting({ company: "예시", title: "겹치는 카드 없음", deadline: { kind: "always" }, sourceUrl: null, applyAt: null, sections: [], keywords: ["없는 키워드"] })
    if (!added.ok) throw new Error("공고를 넣지 못했다")
    expect(await run(() => draftCardPriority(added.postingId))).toEqual({ ok: true, cards: [], reused: false })
    expect(await run(() => draftCardPriority(added.postingId))).toEqual({ ok: true, cards: [], reused: false })
  })

  it("초안: 입력이 같으면 손으로 고친 순서를 앞 초안으로 되돌리고, 서버가 넘기는 카드 칸이 바뀔 때만 새로 만든다", async () => {
    const first = await run(() => draftCardPriority("p-pub1"))
    expect(first).toMatchObject({ ok: true, reused: false })
    if (!first.ok) return
    saveCardPriority("p-pub1", [first.cards[1].cardId])
    expect(await draftCardPriority("p-pub1")).toEqual({ ...first, reused: true })

    // 확인 여부는 서버가 LLM에 넘기지 않는다
    const unverified = listCards().find((c) => !c.verified)
    expect(unverified).toBeDefined()
    verifyCard(unverified?.id ?? "")
    expect(await draftCardPriority("p-pub1")).toMatchObject({ ok: true, reused: true })

    // STAR 칸만 고쳐도 다른 입력이다
    const card = getCard(first.cards[0].cardId)
    updateCard(card.id, { ...card, star: { ...card.star, action: `${card.star.action} 고침` } })
    expect(await run(() => draftCardPriority("p-pub1"))).toMatchObject({ ok: true, reused: false })

    deleteCard(listCards().at(-1)?.id ?? "")
    expect(await run(() => draftCardPriority("p-pub1"))).toMatchObject({ ok: true, reused: false })
  })
})
