import { describe, expect, it } from "vitest"

import { boardCounts, dueText, filterPostings, sortBoard, sortPostings, stageCells, withDecision } from "./status"
import type { MyPosting, MyState } from "./types"

const TODAY = "2026-11-13"
const state = (decision: MyState["decision"], stage: MyState["stage"] = null, result: MyState["result"] = null): MyState => ({ decision, stage, result })
const posting = (id: string, deadline: MyPosting["deadline"], s: MyState = state("undecided")): MyPosting => ({ id, company: id, title: id, deadline, groups: [], ...s })
const on = (date: string) => ({ kind: "date" as const, date })

describe("마감 칸", () => {
  it("날짜는 D-day와 월.일(요일), 3일 안이면 soon", () => {
    expect(dueText({ deadline: on("2026-11-15") }, TODAY)).toEqual({ main: "D-2", sub: "11.15 (일)", soon: true })
    expect(dueText({ deadline: on("2026-11-18") }, TODAY)).toEqual({ main: "D-5", sub: "11.18 (수)", soon: false })
    expect(dueText({ deadline: on("2026-11-10") }, TODAY)).toEqual({ main: "마감", sub: "11.10 (화)", soon: false })
  })
  it("상시와 모름", () => {
    expect(dueText({ deadline: { kind: "always" } }, TODAY).main).toBe("상시")
    expect(dueText({ deadline: { kind: "unknown" } }, TODAY)).toEqual({ main: "확인 필요", sub: "마감일 모름", soon: false })
  })
})

describe("순서", () => {
  it("진행 중은 가까운 마감, 상시, 모름 순이고 마감됨은 최근이 위", () => {
    const list = [posting("모름", { kind: "unknown" }), posting("상시", { kind: "always" }), posting("지남1", on("2026-11-06")), posting("D-5", on("2026-11-18")), posting("지남2", on("2026-11-10")), posting("D-2", on("2026-11-15"))]
    expect(sortPostings(list, TODAY).map((p) => p.id)).toEqual(["D-2", "D-5", "상시", "모름", "지남2", "지남1"])
  })
  it("지원 현황은 지원한 공고만, 떨어진 공고는 아래, 앞선 단계가 위", () => {
    const list = [
      posting("서류떨어짐", on("2026-11-10"), state("applied", "document", "failed")),
      posting("서류", on("2026-11-20"), state("applied", "document", "in_progress")),
      posting("미정", on("2026-11-16")),
      posting("면접", on("2026-11-06"), state("applied", "interview_1", "in_progress")),
      posting("서류빠름", on("2026-11-15"), state("applied", "document", "in_progress")),
    ]
    expect(sortBoard(list).map((p) => p.id)).toEqual(["면접", "서류빠름", "서류", "서류떨어짐"])
  })
})

describe("지원 현황 칸과 결정 바꾸기", () => {
  it("지난 단계는 통과, 지금은 결과, 뒤는 비움", () => {
    expect(stageCells(state("applied", "test", "in_progress"))).toEqual(["pass", "now", "none", "none", "none"])
    expect(stageCells(state("applied", "document", "failed"))).toEqual(["fail", "none", "none", "none", "none"])
    expect(stageCells(state("applied", "final", "passed"))).toEqual(["pass", "pass", "pass", "pass", "pass"])
  })
  it("지원함을 고르면 단계를 채우고, 다른 결정이면 비운다", () => {
    expect(withDecision(state("undecided"), "applied")).toEqual(state("applied", "document", "in_progress"))
    expect(withDecision(state("applied", "test", "in_progress"), "applied")).toEqual(state("applied", "test", "in_progress"))
    expect(withDecision(state("applied", "test", "in_progress"), "hold")).toEqual(state("hold"))
  })
})

describe("내 공고 거르기", () => {
  const list = [
    posting("커머스", on("2026-11-16"), state("undecided")),
    posting("공사", on("2026-11-18"), state("applied", "test", "in_progress")),
    posting("물류", on("2026-11-25"), state("hold")),
    posting("모빌리티", on("2026-11-10"), state("applied", "document", "failed")),
  ]
  it("진행 중과 마감됨을 나누고 수는 전체로 센다", () => {
    const open = filterPostings(list, { view: "open", decision: "all", query: "" }, TODAY)
    expect(open.rows.map((p) => p.id)).toEqual(["커머스", "공사", "물류"])
    expect([open.open, open.closed]).toEqual([3, 1])
    expect(filterPostings(list, { view: "closed", decision: "all", query: "" }, TODAY).rows.map((p) => p.id)).toEqual(["모빌리티"])
  })
  it("결정과 검색어로 거른다", () => {
    expect(filterPostings(list, { view: "open", decision: "applied", query: "" }, TODAY).rows.map((p) => p.id)).toEqual(["공사"])
    expect(filterPostings(list, { view: "open", decision: "all", query: "물" }, TODAY).rows.map((p) => p.id)).toEqual(["물류"])
  })
})

describe("지원 현황의 숫자", () => {
  it("최종이 아닌 단계의 합격은 진행 중에 세고, 넷이 맞아떨어진다", () => {
    const counts = boardCounts([
      state("applied", "document", "passed"),
      state("applied", "test", "in_progress"),
      state("applied", "final", "passed"),
      state("applied", "interview_1", "failed"),
      state("applied", "final", "failed"),
      state("hold"),
    ])
    expect(counts).toEqual({ applied: 5, active: 2, passed: 1, failed: 2 })
    expect(counts.active + counts.passed + counts.failed).toBe(counts.applied)
  })
})
