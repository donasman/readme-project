import { afterEach, describe, expect, it, vi } from "vitest"

import { getExtractionJob, getSources, startExtraction } from "./api"
import type { CardSource } from "./types"

/** 가짜 fetch: 답 하나를 돌려준다. 테스트 모드는 가짜 서버를 쓰지 않아 api가 fetch를 부른다. */
function serve(status: number, body: unknown) {
  vi.stubGlobal("document", { cookie: "" })
  vi.stubGlobal("fetch", async () => new Response(JSON.stringify(body), { status }))
}

afterEach(() => vi.unstubAllGlobals())

const job = (status: string, extra: object = {}) => ({ id: 9, sourceId: 4, status, doneParts: 1, totalParts: 3, cardsSoFar: 2, newCards: 0, skippedSameAs: [], error: null, ...extra })
const source: CardSource = { id: "4", name: "이력서.pdf", createdAt: "2026-11-17", chars: 12000, cardCount: null, runningJobId: null }

describe("카드 뽑기", () => {
  it("서버의 작업을 화면 모양으로 바꾼다(parts → chunks, 소문자 상태)", async () => {
    serve(202, job("queued"))
    expect(await startExtraction(source)).toEqual({
      ok: true,
      job: { id: "9", sourceId: "4", status: "RUNNING", doneChunks: 1, totalChunks: 3, cardsSoFar: 2, newCards: 0, skippedSameAs: [], error: null },
    })
    serve(200, job("failed", { error: "카드를 뽑지 못했습니다" }))
    expect(await getExtractionJob("9")).toMatchObject({ status: "FAILED", error: "카드를 뽑지 못했습니다" })
    serve(200, job("done", { newCards: 2 }))
    expect(await getExtractionJob("9")).toMatchObject({ status: "DONE", newCards: 2 })
  })

  it("이번 달 LLM 한도면 이유를 돌려주고, 작업이 없어졌으면 null", async () => {
    serve(429, { code: "LLM_LIMIT_REACHED", message: "이번 달 AI 사용 한도에 닿았습니다" })
    expect(await startExtraction(source)).toEqual({ ok: false, message: "이번 달 AI 사용 한도에 닿았습니다" })
    serve(404, { code: "JOB_NOT_FOUND", message: "카드 뽑기 작업을 찾을 수 없습니다" })
    expect(await getExtractionJob("9")).toBeNull()
  })

  it("자료 목록에서 뽑는 중인 작업만 이어 볼 작업으로 잡는다", async () => {
    const row = (id: number, latestJob: object | null) => ({ id, name: null, kind: "text", chars: 10, parts: 1, cardCount: null, createdAt: "2026-11-17", latestJob })
    serve(200, [row(1, { id: 7, status: "running", doneParts: 0, totalParts: 1 }), row(2, { id: 8, status: "failed", doneParts: 0, totalParts: 1 }), row(3, null)])
    expect((await getSources()).map((s) => [s.id, s.name, s.runningJobId])).toEqual([
      ["1", "붙여넣은 글", "7"],
      ["2", "붙여넣은 글", null],
      ["3", "붙여넣은 글", null],
    ])
  })
})
