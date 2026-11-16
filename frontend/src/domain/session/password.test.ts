import { describe, expect, it } from "vitest"

import { passwordTooLong } from "./password"

describe("passwordTooLong", () => {
  it("글자 수가 아니라 UTF-8 바이트로 72를 넘는지 본다", () => {
    expect(passwordTooLong("a".repeat(72))).toBe(false)
    expect(passwordTooLong("a".repeat(73))).toBe(true)
    expect(passwordTooLong("가".repeat(24))).toBe(false)
    expect(passwordTooLong("가".repeat(25))).toBe(true)
  })
})
