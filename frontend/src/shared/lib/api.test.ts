import { afterEach, describe, expect, it, vi } from "vitest"

import { ApiError, apiPath, errorMessage, networkError, request } from "./api"

type Call = { url: string; method: string; headers: Record<string, string>; body?: unknown }

/** 가짜 fetch: 답을 차례로 돌려주고 받은 요청을 모은다. 응답마다 쿠키를 바꿀 수 있다(서버가 새 XSRF-TOKEN을 준 것처럼). */
function serve(...answers: { status: number; body?: unknown; cookie?: string }[]) {
  const calls: Call[] = []
  const doc = { cookie: "" }
  vi.stubGlobal("document", doc)
  vi.stubGlobal("fetch", async (url: string, init: RequestInit) => {
    calls.push({ url, method: String(init.method), headers: init.headers as Record<string, string>, body: init.body })
    const answer = answers.shift()!
    if (answer.cookie) doc.cookie = answer.cookie
    return new Response(answer.body === undefined ? null : JSON.stringify(answer.body), { status: answer.status })
  })
  return { calls, doc }
}

afterEach(() => vi.unstubAllGlobals())

describe("errorMessage", () => {
  it("서버가 이유를 준 4xx는 그 글을 문장으로, 끊긴 연결은 연결 안내, 5xx와 code 없는 응답, 다른 오류는 다시 해 보라는 글", () => {
    expect(errorMessage(new ApiError(404, { code: "POST_NOT_FOUND", message: "공고를 찾을 수 없습니다" }))).toBe("공고를 찾을 수 없습니다.")
    expect(errorMessage(networkError())).toBe("서버에 연결하지 못했습니다. 인터넷 연결을 확인하고 다시 해 주세요.")
    expect(errorMessage(new ApiError(500, { code: "INTERNAL_ERROR", message: "x" }))).toBe("서버에 문제가 생겼습니다. 잠시 뒤 다시 해 주세요.")
    expect(errorMessage(new ApiError(413, {}))).toBe("서버에 문제가 생겼습니다. 잠시 뒤 다시 해 주세요.")
    expect(errorMessage(new TypeError("x is undefined"))).toBe("문제가 생겼습니다. 다시 해 주세요.")
  })
})

describe("apiPath", () => {
  it("끼워 넣는 값만 인코딩한다", () => {
    expect(apiPath`/api/groups/${"12"}/posts/${"a/b?c"}`).toBe("/api/groups/12/posts/a%2Fb%3Fc")
    expect(apiPath`/api/groups`).toBe("/api/groups")
  })
})

describe("request", () => {
  it("읽기: 쿠키와 함께 보내고 JSON을 돌려준다. CSRF 값은 싣지 않는다", async () => {
    const { calls, doc } = serve({ status: 200, body: { name: "다온" } })
    doc.cookie = "XSRF-TOKEN=t1"
    expect(await request("GET", "/api/me")).toEqual({ name: "다온" })
    expect(calls[0].headers["X-XSRF-TOKEN"]).toBeUndefined()
  })

  it("바꾸기: CSRF 값과 JSON 본문을 싣고, 204는 undefined", async () => {
    const { calls, doc } = serve({ status: 204 })
    doc.cookie = "SESSION=s; XSRF-TOKEN=t1"
    expect(await request("POST", "/api/auth/logout", { a: 1 })).toBeUndefined()
    expect(calls[0].headers).toEqual({ "Content-Type": "application/json", "X-XSRF-TOKEN": "t1" })
    expect(calls[0].body).toBe('{"a":1}')
  })

  it("파일(FormData)은 JSON으로 바꾸지 않고 Content-Type 없이 보낸다", async () => {
    const { calls } = serve({ status: 201, body: { ok: true } })
    const form = new FormData()
    form.append("file", new Blob(["가나다"]), "이력서.txt")
    await request("POST", "/api/card-sources", form)
    expect(calls[0].headers["Content-Type"]).toBeUndefined()
    expect(calls[0].body).toBe(form)
  })

  it("연결이 안 되면(fetch의 TypeError) 상태 0의 ApiError로 던진다", async () => {
    vi.stubGlobal("document", { cookie: "" })
    vi.stubGlobal("fetch", async () => {
      throw new TypeError("Failed to fetch")
    })
    await expect(request("GET", "/api/me")).rejects.toMatchObject({ status: 0 })
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

  it("id이거나 Id로 끝나는 숫자 칸은 문자열로 읽는다. 다른 숫자와 null은 그대로다", async () => {
    serve(
      { status: 200, body: { id: 1, leaderId: null, members: [{ id: 2 }], openPostCount: 6, me: { postId: 3 } } },
      { status: 409, body: { code: "ALREADY_IN_GROUP", message: "x", postId: 12 } },
    )
    expect(await request("GET", "/api/groups")).toEqual({ id: "1", leaderId: null, members: [{ id: "2" }], openPostCount: 6, me: { postId: "3" } })
    await expect(request("POST", "/api/x")).rejects.toMatchObject({ body: { postId: "12" } })
  })

  it("오류는 서버의 code와 message, 함께 실린 값을 담는다. 본문이 JSON이 아니어도 status는 남는다", async () => {
    serve({ status: 409, body: { code: "ALREADY_IN_GROUP", message: "이미 있습니다", postId: 12 } }, { status: 502 })
    const error = await request("POST", "/api/x").catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, code: "ALREADY_IN_GROUP", message: "이미 있습니다", body: { postId: "12" } })
    await expect(request("GET", "/api/x")).rejects.toMatchObject({ status: 502, code: "" })
  })
})
