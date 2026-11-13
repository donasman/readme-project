import { describe, expect, it } from "vitest"

import { isActive, statusDetail, statusLabel, type MyState } from "./my-state"

const state = (decision: MyState["decision"], stage: MyState["stage"] = null, result: MyState["result"] = null): MyState => ({ decision, stage, result })

describe("상태 글자", () => {
  it("결정만 있으면 결정 이름", () => {
    expect(statusLabel(state("undecided"))).toBe("미정")
    expect(statusLabel(state("skip"))).toBe("지원안함")
  })
  it("지원하고 서류 진행 중이면 지원함, 그 뒤는 단계와 결과", () => {
    expect(statusLabel(state("applied", "document", "in_progress"))).toBe("지원함")
    expect(statusLabel(state("applied", "test", "in_progress"))).toBe("필기 단계")
    expect(statusLabel(state("applied", "document", "failed"))).toBe("서류 불합격")
    expect(statusLabel(state("applied", "final", "passed"))).toBe("최종 합격")
  })
  it("상세는 지원함과 단계를 함께", () => {
    expect(statusDetail(state("applied", "test", "in_progress"))).toBe("지원함 · 필기 단계")
    expect(statusDetail(state("hold"))).toBe("보류")
  })
  it("떨어진 공고는 강조하지 않는다", () => {
    expect(isActive(state("applied", "interview_1", "in_progress"))).toBe(true)
    expect(isActive(state("applied", "document", "failed"))).toBe(false)
    expect(isActive(state("undecided"))).toBe(false)
  })
})
