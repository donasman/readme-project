import { describe, expect, it } from "vitest"

import { addMonths, formatMonth, isMonth, monthCells, monthEvents, monthOf } from "./calendar"
import type { MyPosting } from "./types"

const TODAY = "2026-11-13"
const posting = (id: string, deadline: MyPosting["deadline"]): MyPosting => ({ id, company: id, title: id, deadline, groups: [], decision: "undecided", stage: null, result: null })

describe("달", () => {
  it("달 옮기기와 글자", () => {
    expect(monthOf(TODAY)).toBe("2026-11")
    expect(addMonths("2026-12", 1)).toBe("2027-01")
    expect(addMonths("2026-01", -1)).toBe("2025-12")
    expect(formatMonth("2026-11")).toBe("2026년 11월")
    expect(isMonth("2026-13")).toBe(false)
    expect(isMonth("2026-02")).toBe(true)
  })
})

describe("달력 칸", () => {
  const list = [posting("보안", { kind: "date", date: "2026-11-06" }), posting("제조", { kind: "date", date: "2026-11-15" }), posting("상시", { kind: "always" }), posting("12월", { kind: "date", date: "2026-12-03" })]
  it("2026년 11월은 일요일에 시작해 5주", () => {
    const cells = monthCells("2026-11", list, TODAY)
    expect(cells).toHaveLength(35)
    expect(cells[0].day).toBe(1)
    expect(cells[30].day).toBe(null)
    expect(cells[12]).toMatchObject({ day: 13, isToday: true, isPast: false })
    expect(cells[5]).toMatchObject({ day: 6, isPast: true, events: [{ company: "보안", closed: true }] })
  })
  it("앞뒤 빈칸을 채운다(2026년 12월은 화요일 시작)", () => {
    const cells = monthCells("2026-12", list, TODAY)
    expect(cells.slice(0, 3).map((c) => c.day)).toEqual([null, null, 1])
    expect(cells.length % 7).toBe(0)
  })
  it("그달의 마감만, 상시는 빼고", () => {
    expect(monthEvents("2026-11", list, TODAY).map((e) => [e.company, e.closed])).toEqual([["보안", true], ["제조", false]])
  })
})
