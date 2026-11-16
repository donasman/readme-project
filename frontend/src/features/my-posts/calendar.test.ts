import { describe, expect, it } from "vitest"

import { addMonths, formatMonth, isMonth, monthCells, monthEvents, monthOf, monthSchedules } from "./calendar"
import type { MyPosting, Schedule } from "./types"

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
    const cells = monthCells("2026-11", list, TODAY, [])
    expect(cells).toHaveLength(35)
    expect(cells[0].day).toBe(1)
    expect(cells[30].day).toBe(null)
    expect(cells[12]).toMatchObject({ day: 13, isToday: true, isPast: false })
    expect(cells[5]).toMatchObject({ day: 6, isPast: true, events: [{ company: "보안", closed: true }] })
  })
  it("앞뒤 빈칸을 채운다(2026년 12월은 화요일 시작)", () => {
    const cells = monthCells("2026-12", list, TODAY, [])
    expect(cells.slice(0, 3).map((c) => c.day)).toEqual([null, null, 1])
    expect(cells.length % 7).toBe(0)
  })
  it("칩에 남은 날, 3일 안인지, 지원했는지를 싣는다", () => {
    const applied: MyPosting = { ...posting("핀테크", { kind: "date", date: "2026-11-20" }), decision: "applied", stage: "document", result: "in_progress" }
    const events = monthEvents("2026-11", [...list, applied], TODAY)
    expect(events.map((e) => [e.company, e.due, e.soon, e.applied])).toEqual([
      ["보안", "마감", false, false],
      ["제조", "D-2", true, false],
      ["핀테크", "D-7", false, true],
    ])
  })
  it("그달의 마감만, 상시는 빼고", () => {
    expect(monthEvents("2026-11", list, TODAY).map((e) => [e.company, e.closed])).toEqual([["보안", true], ["제조", false]])
  })
})

describe("전형 일정", () => {
  const at = (date: string, time: string | null, company = "보안"): Schedule => ({ postingId: "1", company, title: "서버", stage: "interview_1", date, time })
  it("그달의 일정만 날짜와 시각 순으로, 시각이 없는 것을 그날 앞에 둔다", () => {
    const list = [at("2026-11-20", "14:00", "늦게"), at("2026-12-01", null), at("2026-11-20", null, "시각없음"), at("2026-11-17", "10:00")]
    expect(monthSchedules("2026-11", list).map((s) => [s.date, s.company])).toEqual([["2026-11-17", "보안"], ["2026-11-20", "시각없음"], ["2026-11-20", "늦게"]])
  })
  it("일정은 그날 칸에 들어간다", () => {
    const cells = monthCells("2026-11", [], TODAY, [at("2026-11-17", "10:00")])
    expect(cells.find((c) => c.day === 17)?.schedules.map((s) => s.time)).toEqual(["10:00"])
    expect(cells.find((c) => c.day === 18)?.schedules).toEqual([])
  })
})
