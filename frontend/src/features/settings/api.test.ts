import { afterEach, describe, expect, it, vi } from "vitest"

import { changePassword, withdraw } from "./api"

/** 가짜 fetch: 답 하나를 돌려준다. 테스트 모드는 가짜 서버를 쓰지 않아 changePassword가 fetch를 부른다. */
function serve(status: number, body?: unknown) {
  vi.stubGlobal("document", { cookie: "" })
  vi.stubGlobal("fetch", async () => new Response(body === undefined ? null : JSON.stringify(body), { status }))
}

afterEach(() => vi.unstubAllGlobals())

describe("changePassword", () => {
  it("바꾸면 done, 지금 비밀번호가 틀리면 wrong, 너무 많이 틀렸으면 throttled, 비밀번호가 없는 계정이면 none", async () => {
    serve(204)
    expect(await changePassword("readme1234", "newpass99")).toBe("done")
    serve(400, { code: "WRONG_PASSWORD", message: "비밀번호가 맞지 않습니다" })
    expect(await changePassword("x", "newpass99")).toBe("wrong")
    serve(429, { code: "TOO_MANY_ATTEMPTS", message: "시도가 너무 많습니다. 잠시 뒤에 다시 시도해 주세요" })
    expect(await changePassword("x", "newpass99")).toBe("throttled")
    serve(409, { code: "NO_PASSWORD", message: "비밀번호가 없는 계정입니다" })
    expect(await changePassword("x", "newpass99")).toBe("none")
  })

  it("다른 실패는 그대로 던진다", async () => {
    serve(500, { code: "INTERNAL", message: "오류" })
    await expect(changePassword("x", "newpass99")).rejects.toMatchObject({ status: 500 })
  })
})

describe("withdraw", () => {
  it("탈퇴하면 done, 확인이 틀리면 wrong, 너무 많이 틀렸으면 throttled", async () => {
    serve(204)
    expect(await withdraw({ password: "readme1234" })).toBe("done")
    serve(400, { code: "WRONG_EMAIL", message: "계정의 이메일과 맞지 않습니다" })
    expect(await withdraw({ email: "x@example.com" })).toBe("wrong")
    serve(429, { code: "TOO_MANY_ATTEMPTS", message: "시도가 너무 많습니다. 잠시 뒤에 다시 시도해 주세요" })
    expect(await withdraw({ password: "x" })).toBe("throttled")
  })
})
