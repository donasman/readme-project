// 달력의 칸과 그달의 마감 목록. 화면과 떨어진 순수 함수다. 달은 "YYYY-MM".
import { isClosed } from "@/domain/posting/deadline"
import { daysBetween } from "@/shared/lib/date"

import type { MyPosting } from "./types"

/** 날짜의 달("2026-11-13" → "2026-11"). */
export const monthOf = (date: string) => date.slice(0, 7)

/** 달을 n달 옮긴다("2026-12", 1 → "2027-01"). */
export function addMonths(month: string, n: number): string {
  const [y, m] = month.split("-").map(Number)
  const index = y * 12 + (m - 1) + n
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, "0")}`
}

/** "2026년 11월" */
export function formatMonth(month: string): string {
  const [y, m] = month.split("-").map(Number)
  return `${y}년 ${m}월`
}

/** 바른 달 글자인지("2026-11"). */
export const isMonth = (value: string | null): value is string => !!value && /^\d{4}-(0[1-9]|1[0-2])$/.test(value)

export interface DeadlineEvent {
  postingId: string
  company: string
  title: string
  date: string
  /** 마감일이 오늘보다 앞 */
  closed: boolean
}

export interface DayCell {
  /** 그달이 아닌 칸은 null */
  date: string | null
  day: number | null
  isToday: boolean
  isPast: boolean
  events: DeadlineEvent[]
}

/** 그달에 마감하는 내 공고(마감일 순). 상시와 마감일 모름은 놓이지 않는다. */
export function monthEvents(month: string, postings: MyPosting[], today: string): DeadlineEvent[] {
  return postings
    .flatMap((p) =>
      p.deadline.kind === "date" && monthOf(p.deadline.date) === month
        ? [{ postingId: p.id, company: p.company, title: p.title, date: p.deadline.date, closed: isClosed(p.deadline, today) }]
        : []
    )
    .sort((a, b) => a.date.localeCompare(b.date) || a.company.localeCompare(b.company))
}

/** 일요일부터 시작하는 주 단위 칸. 그달 앞뒤의 빈칸을 채워 7의 배수로 만든다. */
export function monthCells(month: string, postings: MyPosting[], today: string): DayCell[] {
  const [y, m] = month.split("-").map(Number)
  const offset = new Date(Date.UTC(y, m - 1, 1)).getUTCDay()
  const days = new Date(Date.UTC(y, m, 0)).getUTCDate()
  const events = monthEvents(month, postings, today)
  const total = Math.ceil((offset + days) / 7) * 7
  return Array.from({ length: total }, (_, i) => {
    const day = i - offset + 1
    if (day < 1 || day > days) return { date: null, day: null, isToday: false, isPast: false, events: [] }
    const date = `${month}-${String(day).padStart(2, "0")}`
    return { date, day, isToday: date === today, isPast: daysBetween(today, date) < 0, events: events.filter((e) => e.date === date) }
  })
}
