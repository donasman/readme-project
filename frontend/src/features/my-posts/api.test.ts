import { afterEach, describe, expect, it, vi } from "vitest"

import type { PostUpload } from "@/domain/posting/types"

import { addMyPosting, getMyPosting } from "./api"

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
    serve(200, detail(analysis, [{ cardId: 5, title: "주문 조회 개선", overlap: ["Java"] }], ["Kotlin"]))
    const { posting } = await getMyPosting("3")
    expect(posting).toMatchObject({ analysis, memo: "", matches: [{ cardId: "5", title: "주문 조회 개선", overlap: ["Java"] }], unmatched: ["Kotlin"] })
  })
})
