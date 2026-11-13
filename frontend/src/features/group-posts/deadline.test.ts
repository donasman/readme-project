import { describe, expect, it } from "vitest"

import { daysLeft, deadlineText, isClosed, isNewPost } from "./deadline"
import type { Deadline } from "./types"

const TODAY = "2026-11-13" // 금요일
const on = (date: string): Deadline => ({ kind: "date", date })
const ALWAYS: Deadline = { kind: "always" }
const UNKNOWN: Deadline = { kind: "unknown" }

describe("isClosed", () => {
  it("마감일이 오늘보다 앞이면 마감됨", () => {
    expect(isClosed(on("2026-11-12"), TODAY)).toBe(true)
  })
  it("마감일 당일은 아직 진행 중", () => {
    expect(isClosed(on(TODAY), TODAY)).toBe(false)
  })
  it("상시 채용과 날짜를 모르는 공고는 마감되지 않는다", () => {
    expect(isClosed(ALWAYS, TODAY)).toBe(false)
    expect(isClosed(UNKNOWN, TODAY)).toBe(false)
  })
})

describe("daysLeft", () => {
  it("마감까지 남은 날, 지났으면 음수", () => {
    expect(daysLeft(on("2026-11-16"), TODAY)).toBe(3)
    expect(daysLeft(on("2026-11-10"), TODAY)).toBe(-3)
  })
  it("날짜가 없으면 null", () => {
    expect(daysLeft(ALWAYS, TODAY)).toBeNull()
    expect(daysLeft(UNKNOWN, TODAY)).toBeNull()
  })
})

describe("deadlineText", () => {
  it("남은 날은 D-n과 요일 붙은 날짜", () => {
    expect(deadlineText(on("2026-11-16"), TODAY)).toEqual({ main: "D-3", sub: "11.16 (월)" })
  })
  it("마감일 당일은 D-Day", () => {
    expect(deadlineText(on(TODAY), TODAY)).toEqual({ main: "D-Day", sub: "11.13 (금)" })
  })
  it("지난 마감은 마감됨과 마감 날짜", () => {
    expect(deadlineText(on("2026-11-05"), TODAY)).toEqual({ main: "마감됨", sub: "11.05 마감" })
  })
  it("상시 채용, 날짜 모름", () => {
    expect(deadlineText(ALWAYS, TODAY)).toEqual({ main: "상시", sub: "수시 채용" })
    expect(deadlineText(UNKNOWN, TODAY)).toEqual({ main: "마감일 확인 필요", sub: "" })
  })
})

describe("isNewPost: 올린 지 3일 이내이고 마감되지 않았으면 새 공고", () => {
  it("오늘부터 3일 전까지 올린 공고", () => {
    expect(isNewPost(TODAY, ALWAYS, TODAY)).toBe(true)
    expect(isNewPost("2026-11-10", ALWAYS, TODAY)).toBe(true)
  })
  it("4일 전에 올린 공고는 아니다", () => {
    expect(isNewPost("2026-11-09", ALWAYS, TODAY)).toBe(false)
  })
  it("마감된 공고는 아니다", () => {
    expect(isNewPost(TODAY, on("2026-11-12"), TODAY)).toBe(false)
  })
  it("올린 날이 오늘보다 뒤면(시계가 어긋난 데이터) 아니다", () => {
    expect(isNewPost("2026-11-14", ALWAYS, TODAY)).toBe(false)
  })
})
