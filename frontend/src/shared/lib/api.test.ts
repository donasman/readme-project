import { afterEach, describe, expect, it, vi } from "vitest"

import { ApiError, apiPath, request } from "./api"

type Call = { url: string; method: string; headers: Record<string, string>; body?: string }

/** 가짜 fetch: 답을 차례로 돌려주고 받은 요청을 모은다. 응답마다 쿠키를 바꿀 수 있다(서버가 새 XSRF-TOKEN을 준 것처럼). */
function serve(...answers: { status: number; body?: unknown; cookie?: string }[]) {
  const calls: Call[] = []
  const doc = { cookie: "" }
  vi.stubGlobal("document", doc)
  vi.stubGlobal("fetch", async (url: string, init: RequestInit) => {
    calls.push({ url, method: String(init.method), headers: init.headers as Record<string, string>, body: init.body as string | undefined })
    const answer = answers.shift()!
    if (answer.cookie) doc.cookie = answer.cookie
    return new Response(answer.body === undefined ? null : JSON.stringify(answer.body), { status: answer.status })
  })
  return { calls, doc }
}

afterEach(() => vi.unstubAllGlobals())

describe("apiPath", () => {
  it("끼워 넣는 값만 인코딩한다", () => {
    expect(apiPath`/api/groups/${"12"}/posts/${"a/b?c"}`).toBe("/api/groups/12/posts/a%2Fb%3Fc")
    expect(apiPath`/api/groups`).toBe("/api/groups")
  })
})

describe("request", () => {
  it("읽기: 쿠키와 함께 보내고 JSON을 돌려준다. CSRF 값은 싣지 않는다", async () => {
    const { calls, doc } = serve({ status: 200, body: { id: 1 } })
    doc.cookie = "XSRF-TOKEN=t1"
    expect(await request("GET", "/api/me")).toEqual({ id: 1 })
    expect(calls[0].headers["X-XSRF-TOKEN"]).toBeUndefined()
  })

  it("바꾸기: CSRF 값과 JSON 본문을 싣고, 204는 undefined", async () => {
    const { calls, doc } = serve({ status: 204 })
    doc.cookie = "SESSION=s; XSRF-TOKEN=t1"
    expect(await request("POST", "/api/auth/logout", { a: 1 })).toBeUndefined()
    expect(calls[0].headers).toEqual({ "Content-Type": "application/json", "X-XSRF-TOKEN": "t1" })
    expect(calls[0].body).toBe('{"a":1}')
  })

  it("CSRF로 막히면 서버가 새로 준 쿠키로 한 번만 다시 보낸다", async () => {
    const { calls } = serve({ status: 403, body: { code: "CSRF_INVALID", message: "x" }, cookie: "XSRF-TOKEN=t2" }, { status: 204 })
    await request("POST", "/api/auth/login", {})
    expect(calls).toHaveLength(2)
    expect(calls[0].headers["X-XSRF-TOKEN"]).toBeUndefined()
    expect(calls[1].headers["X-XSRF-TOKEN"]).toBe("t2")
  })

  it("두 번째도 막히면 더 보내지 않고 던진다", async () => {
    const csrf = { status: 403, body: { code: "CSRF_INVALID", message: "x" } }
    const { calls } = serve(csrf, csrf)
    await expect(request("POST", "/api/x")).rejects.toMatchObject({ status: 403, code: "CSRF_INVALID" })
    expect(calls).toHaveLength(2)
  })

  it("오류는 서버의 code와 message, 함께 실린 값을 담는다. 본문이 JSON이 아니어도 status는 남는다", async () => {
    serve({ status: 409, body: { code: "ALREADY_IN_GROUP", message: "이미 있습니다", postId: 12 } }, { status: 502 })
    const error = await request("POST", "/api/x").catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, code: "ALREADY_IN_GROUP", message: "이미 있습니다", body: { postId: 12 } })
    await expect(request("GET", "/api/x")).rejects.toMatchObject({ status: 502, code: "" })
  })
})
