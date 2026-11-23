import { afterEach, describe, expect, it, vi } from "vitest"

import type { PostUpload } from "@/domain/posting/types"

import { addMyPosting, getMyPosting, matchQuestions, saveQuestions } from "./api"

/** 가짜 fetch: 답 하나를 돌려준다. 테스트 모드는 가짜 서버를 쓰지 않아 addMyPosting이 fetch를 부른다. */
function serve(status: number, body: unknown) {
  vi.stubGlobal("document", { cookie: "" })
  vi.stubGlobal("fetch", async () => new Response(JSON.stringify(body), { status }))
}

afterEach(() => vi.unstubAllGlobals())

const upload: PostUpload = { company: "예시랩", title: "백엔드", deadline: { kind: "always" }, sourceUrl: null, applyAt: null, sections: [], keywords: [] }

describe("addMyPosting", () => {
  it("넣으면 새 공고의 id, 이미 있으면 409의 postingId를 돌려준다", async () => {
    serve(201, { today: "2026-11-15", posting: { id: 12 } })
    expect(await addMyPosting(upload)).toEqual({ ok: true, postingId: "12" })
    serve(409, { code: "ALREADY_IN_MY_POSTINGS", message: "이미 내 공고에 있습니다", postingId: 7 })
    expect(await addMyPosting(upload)).toEqual({ ok: false, error: "ALREADY_IN_MY_POSTINGS", postingId: "7" })
  })
})

describe("getMyPosting", () => {
  const detail = (analysis: unknown, matches: unknown[], unmatched: string[]) => ({
    today: "2026-11-17",
    posting: { id: 3, company: "예시", title: "백엔드", memo: null, analysis, matches, unmatched, groupStates: [] },
  })

  it("공고 분석, 서버가 센 맞는 카드, 답하지 못한 요건을 그대로 쓰고 빈 메모는 빈 글로 읽는다", async () => {
    const analysis = { must: ["Java"], nice: [], duties: ["API 개발"], keywords: ["Java"], questions: [{ text: "지원 동기", limit: 800 }] }
    serve(200, detail(analysis, [{ cardId: 5, title: "주문 조회 개선", experience: "", overlap: ["Java"] }], ["Kotlin"]))
    const { posting } = await getMyPosting("3")
    expect(posting).toMatchObject({ analysis, memo: "", matches: [{ cardId: "5", title: "주문 조회 개선", experience: "", overlap: ["Java"] }], unmatched: ["Kotlin"] })
  })

  it("요건마다 맞춘 카드의 id를 글자로 읽는다", async () => {
    const requirements = [{ kind: "must", text: "Java", status: "matched", cards: [{ cardId: 5, overlap: ["Java"] }], profileItems: [] }]
    serve(200, { today: "2026-11-19", posting: { id: 3, memo: null, requirements } })
    const { posting } = await getMyPosting("3")
    expect(posting.requirements[0].cards).toEqual([{ cardId: "5", overlap: ["Java"] }])
  })
})

describe("문항 매칭", () => {
  it("문항 id와 카드 id를 글자로 읽고, 저장 전 문항의 id는 null로 둔다", async () => {
    const questions = [
      { id: 3, text: "실패 경험", limit: 800, type: "FAILURE", cards: [{ cardId: 12, title: "t", experience: "", reason: "r", emphasis: ["obstacle"] }] },
      { id: null, text: "지원 동기", limit: null, type: null, cards: [] },
    ]
    serve(200, { today: "2026-11-24", posting: { id: 3, memo: null, questions } })
    const { posting } = await getMyPosting("3")
    expect(posting.questions.map((q) => q.id)).toEqual(["3", null])
    expect(posting.questions[0].cards[0].cardId).toBe("12")
  })

  it("문항을 숫자 id로 보낸다(새 문항은 null)", async () => {
    let sent: unknown
    vi.stubGlobal("document", { cookie: "" })
    vi.stubGlobal("fetch", async (_url: string, init: RequestInit) => {
      sent = JSON.parse(String(init.body))
      return new Response(JSON.stringify({ questions: [] }), { status: 200 })
    })
    await saveQuestions("3", [{ id: "7", text: "a", limit: 800 }, { id: null, text: "b", limit: null }])
    expect(sent).toEqual({ questions: [{ id: 7, text: "a", limit: 800 }, { id: null, text: "b", limit: null }] })
  })

  it("한도·카드 없음·문항 없음은 결과로, 나머지 오류는 던진다", async () => {
    serve(429, { code: "LLM_LIMIT_REACHED", message: "한도" })
    expect(await matchQuestions("3")).toEqual({ ok: false, error: "LLM_LIMIT_REACHED" })
    serve(409, { code: "NO_QUESTIONS", message: "문항 없음" })
    expect(await matchQuestions("3")).toEqual({ ok: false, error: "NO_QUESTIONS" })
    serve(502, { code: "LLM_FAILED", message: "실패" })
    await expect(matchQuestions("3")).rejects.toThrow()
  })
})
