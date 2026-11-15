import { describe, expect, it } from "vitest"

import { sourceParagraphs } from "./post-source"

describe("sourceParagraphs", () => {
  it("머리 줄에서 문단을 열고 빈 줄에서 닫는다", () => {
    const text = "회사 소개\n예시랩입니다.\n\n담당 업무\n- 주문 API 개발\n- 배치 개선\n\n접수: 채용 시 마감"
    expect(sourceParagraphs(text, ["담당 업무", "회사 소개", null])).toEqual([
      { heading: "회사 소개", text: "예시랩입니다." },
      { heading: "담당 업무", text: "- 주문 API 개발\n- 배치 개선" },
      { heading: null, text: "접수: 채용 시 마감" },
    ])
  })
  it("머리 줄이 없으면 빈 줄로만 나눈다. 앞뒤 공백 줄은 버린다", () => {
    expect(sourceParagraphs("\n  첫 줄 \n둘째 줄\n\n\n셋째 줄\n", [])).toEqual([
      { heading: null, text: "첫 줄\n둘째 줄" },
      { heading: null, text: "셋째 줄" },
    ])
  })
})
