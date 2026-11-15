import { afterEach, describe, expect, it, vi } from "vitest"

import type { PostUpload } from "@/domain/posting/types"

import { addMyPosting } from "./api"

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
