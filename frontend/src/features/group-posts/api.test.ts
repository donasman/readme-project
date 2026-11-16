import { afterEach, describe, expect, it, vi } from "vitest"

import { editPost } from "./api"

/** 가짜 fetch: 답 하나를 돌려주고 받은 요청을 남긴다. 테스트 모드는 가짜 서버를 쓰지 않아 editPost가 fetch를 부른다. */
function serve(status: number, body?: unknown) {
  const calls: { url: string; init: RequestInit }[] = []
  vi.stubGlobal("document", { cookie: "" })
  vi.stubGlobal("fetch", async (url: string, init: RequestInit) => {
    calls.push({ url, init })
    return new Response(body === undefined ? null : JSON.stringify(body), { status })
  })
  return calls
}

afterEach(() => vi.unstubAllGlobals())

const edit = { company: "예시 커머스", title: "백엔드 개발자", deadline: { kind: "date", date: "2026-11-30" } } as const

describe("editPost", () => {
  it("고친 값을 PUT으로 보내고, 고치면 ok", async () => {
    const calls = serve(200, { today: "2026-11-16", post: {} })
    expect(await editPost("1", "7", edit)).toEqual({ ok: true })
    expect(calls[0].url).toContain("/api/groups/1/posts/7")
    expect(calls[0].init.method).toBe("PUT")
    expect(JSON.parse(String(calls[0].init.body))).toEqual(edit)
  })

  it("남과 함께 쓰는 공고면 서버의 까닭을 그대로 돌려준다", async () => {
    serve(409, { code: "POSTING_SHARED", message: "다른 사람이 다른 그룹에도 올린 공고라 고칠 수 없습니다" })
    expect(await editPost("1", "7", edit)).toEqual({ ok: false, error: "POSTING_SHARED", message: "다른 사람이 다른 그룹에도 올린 공고라 고칠 수 없습니다" })
  })

  it("다른 실패는 그대로 던진다", async () => {
    serve(409, { code: "DEADLINE_ALREADY_SET", message: "이미 마감일이 들어간 공고입니다" })
    await expect(editPost("1", "7", edit)).rejects.toMatchObject({ status: 409, code: "DEADLINE_ALREADY_SET" })
  })
})
