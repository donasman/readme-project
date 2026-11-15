import { afterEach, describe, expect, it, vi } from "vitest"

import { readPost } from "./api"

/** 가짜 fetch: 답 하나를 돌려주고 받은 본문을 모은다. 테스트 모드는 가짜 서버를 쓰지 않아 readPost가 fetch를 부른다. */
function serve(status: number, body?: unknown) {
  const bodies: unknown[] = []
  vi.stubGlobal("document", { cookie: "" })
  vi.stubGlobal("fetch", async (_url: string, init: RequestInit) => {
    bodies.push(init.body)
    return new Response(body === undefined ? null : JSON.stringify(body), { status })
  })
  return bodies
}

afterEach(() => vi.unstubAllGlobals())

const file = { kind: "file" as const, file: new File(["공고"], "공고.pdf") }

describe("readPost의 파일", () => {
  it("multipart로 파일과 그룹 id를 보낸다", async () => {
    const bodies = serve(422, { code: "FILE_NO_TEXT", message: "글자를 읽을 수 없는 파일입니다" })
    await readPost("4", file)
    const sent = bodies[0] as FormData
    expect(sent).toBeInstanceOf(FormData)
    expect((sent.get("file") as File).name).toBe("공고.pdf")
    expect(sent.get("groupId")).toBe("4")
  })

  it("서버가 이유를 준 파일 오류는 그 글로 알린다", async () => {
    serve(422, { code: "FILE_NO_TEXT", message: "글자를 읽을 수 없는 파일입니다" })
    expect(await readPost("4", file)).toEqual({ status: "failed", message: "글자를 읽을 수 없는 파일입니다." })
  })

  it("본문 없는 413(서버 앞의 Caddy)은 너무 크다고 알린다", async () => {
    serve(413)
    expect(await readPost("4", file)).toEqual({ status: "failed", message: "파일이 너무 큽니다. 20MB까지 올릴 수 있습니다." })
  })

  it("알릴 이유가 없는 오류는 그대로 던진다(화면 아래 알림이 받는다)", async () => {
    serve(500, { code: "INTERNAL_ERROR", message: "x" })
    await expect(readPost("4", file)).rejects.toMatchObject({ status: 500 })
  })
})
