import { describe, expect, it } from "vitest"

import { readFileProblem } from "./read-file"

const MB = 1024 * 1024

describe("readFileProblem", () => {
  it("문서와 사진은 받고 다른 형식은 막는다", () => {
    expect(readFileProblem({ name: "공고.pdf", size: MB })).toBeNull()
    expect(readFileProblem({ name: "공고.HWP", size: MB })).toBeNull()
    expect(readFileProblem({ name: "image.png", size: MB })).toBeNull()
    expect(readFileProblem({ name: "캡처.JPEG", size: MB })).toBeNull()
    expect(readFileProblem({ name: "저장.jfif", size: MB })).toBeNull()
    expect(readFileProblem({ name: "사진.heic", size: MB })).toMatch("만 올릴 수 있습니다")
    expect(readFileProblem({ name: "공고.pptx", size: MB })).toMatch("만 올릴 수 있습니다")
  })

  it("사진은 5MB, 문서는 20MB까지다", () => {
    expect(readFileProblem({ name: "image.png", size: 5 * MB })).toBeNull()
    expect(readFileProblem({ name: "image.png", size: 5 * MB + 1 })).toBe("사진은 5MB까지 올릴 수 있습니다.")
    expect(readFileProblem({ name: "공고.pdf", size: 20 * MB })).toBeNull()
    expect(readFileProblem({ name: "공고.pdf", size: 20 * MB + 1 })).toMatch("20MB")
  })
})
