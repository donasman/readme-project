import { afterEach, describe, expect, it, vi } from "vitest"

import { getInvites } from "./api"

/** 가짜 fetch: 답 하나를 돌려준다. 테스트 모드는 가짜 서버를 쓰지 않아 getInvites가 fetch를 부른다. */
function serve(status: number, body: unknown) {
  vi.stubGlobal("document", { cookie: "" })
  vi.stubGlobal("fetch", async () => new Response(JSON.stringify(body), { status }))
}

afterEach(() => vi.unstubAllGlobals())

describe("getInvites", () => {
  it("한국 시각을 분까지, 만료는 날짜만 남기고 만든 사람의 id는 문자열로 읽는다", async () => {
    serve(200, [
      { id: 9, createdBy: { id: 1, name: "다온" }, createdAt: "2026-11-16T10:12:33.123456+09:00", expiresAt: "2026-11-23T10:12:33.123456+09:00" },
      { id: 4, createdBy: null, createdAt: "2026-11-10T09:40:00+09:00", expiresAt: "2026-11-17T09:40:00+09:00" },
    ])
    expect(await getInvites("3")).toEqual([
      { id: "9", createdBy: { id: "1", name: "다온" }, createdAt: "2026-11-16 10:12", expiresAt: "2026-11-23" },
      { id: "4", createdBy: null, createdAt: "2026-11-10 09:40", expiresAt: "2026-11-17" },
    ])
  })
})
