import { describe, expect, it } from "vitest"

import { splitFlagged } from "./card-text"

describe("확인 필요 표시", () => {
  it("글자와 표시로 나눈다", () => {
    expect(splitFlagged("문의도 [확인 필요: 30%] 줄었다.")).toEqual([
      { text: "문의도 " },
      { text: "확인 필요: 30%", flagged: true },
      { text: " 줄었다." },
    ])
    expect(splitFlagged("표시 없음")).toEqual([{ text: "표시 없음" }])
  })
})
