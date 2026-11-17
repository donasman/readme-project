import { describe, expect, it } from "vitest"

import { formatQuestionLimit, summarySections } from "./analysis"

describe("공고 분석 요약", () => {
  it("업무, 필수, 우대 순으로 묶고 빈 묶음은 뺀다", () => {
    const sections = summarySections({ must: ["Java"], nice: [], duties: ["API 개발"], keywords: [], questions: [] })
    expect(sections).toEqual([
      { title: "주요 업무", items: ["API 개발"] },
      { title: "필수 역량", items: ["Java"] },
    ])
  })

  it("문항 글자 수는 천 단위로 끊고, 없으면 없다고 적는다", () => {
    expect(formatQuestionLimit(1000)).toBe("1,000자")
    expect(formatQuestionLimit(null)).toBe("글자 수 없음")
  })
})
