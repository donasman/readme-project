// 날짜 계산과 표시 글자. 화면과 떨어진 순수 함수다. 날짜는 "YYYY-MM-DD".
import type { Deadline } from "./types"

const WEEKDAYS = "일월화수목금토"

function toUtcDay(date: string): number {
  const [y, m, d] = date.split("-").map(Number)
  return Date.UTC(y, m - 1, d) / 86_400_000
}

/** from에서 to까지 며칠인지. to가 앞이면 음수. */
export function daysBetween(from: string, to: string): number {
  return toUtcDay(to) - toUtcDay(from)
}

/** "11.05" */
export function formatMonthDay(date: string): string {
  const [, m, d] = date.split("-")
  return `${m}.${d}`
}

/** "11.16 (월)" */
export function formatMonthDayWeekday(date: string): string {
  const [y, m, d] = date.split("-").map(Number)
  return `${formatMonthDay(date)} (${WEEKDAYS[new Date(Date.UTC(y, m - 1, d)).getUTCDay()]})`
}

/** 마감일이 오늘보다 앞이면 마감됨. 마감일 당일은 아직 진행 중이다. */
export function isClosed(deadline: Deadline, today: string): boolean {
  return deadline.kind === "date" && daysBetween(today, deadline.date) < 0
}

/** 마감까지 남은 날. 날짜가 없으면 null. */
export function daysLeft(deadline: Deadline, today: string): number | null {
  return deadline.kind === "date" ? daysBetween(today, deadline.date) : null
}

/** 마감 칸의 큰 글자와 작은 글자. */
export function deadlineText(deadline: Deadline, today: string): { main: string; sub: string } {
  switch (deadline.kind) {
    case "unknown":
      return { main: "마감일 확인 필요", sub: "" }
    case "always":
      return { main: "상시", sub: "수시 채용" }
    case "date": {
      const left = daysBetween(today, deadline.date)
      if (left < 0) return { main: "마감됨", sub: `${formatMonthDay(deadline.date)} 마감` }
      return { main: left === 0 ? "D-Day" : `D-${left}`, sub: formatMonthDayWeekday(deadline.date) }
    }
  }
}

/** 올린 지 3일 이내이고 마감되지 않았으면 "새 공고". */
export function isNewPost(postedAt: string, deadline: Deadline, today: string): boolean {
  const age = daysBetween(postedAt, today)
  return age >= 0 && age <= 3 && !isClosed(deadline, today)
}

/** 댓글 시각: 1분 안이면 "방금", 아니면 한국 날짜로 "11.05". */
export function formatCommentTime(createdAt: string, now: number = Date.now()): string {
  const at = Date.parse(createdAt)
  if (now - at < 60_000) return "방금"
  const parts = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", month: "2-digit", day: "2-digit" })
    .formatToParts(at)
  const pick = (type: string) => parts.find((p) => p.type === type)?.value ?? ""
  return `${pick("month")}.${pick("day")}`
}
