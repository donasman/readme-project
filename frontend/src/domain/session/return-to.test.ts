import { describe, expect, it } from "vitest"

import { isAppPath } from "./return-to"

describe("isAppPath", () => {
  it("이 앱의 주소만 받는다", () => {
    expect(isAppPath("/invite/Xk3f9QmT2a")).toBe(true)
    expect(isAppPath("/")).toBe(true)
    expect(isAppPath("//evil.example/x")).toBe(false)
    expect(isAppPath("/\\evil.example")).toBe(false)
    expect(isAppPath("https://evil.example")).toBe(false)
    expect(isAppPath("")).toBe(false)
  })
})
