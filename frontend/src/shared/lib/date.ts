// 날짜 계산과 표시 글자. 화면과 떨어진 순수 함수다. 날짜는 "YYYY-MM-DD".

const WEEKDAYS = "일월화수목금토"

function toUtcDay(date: string): number {
  const [y, m, d] = date.split("-").map(Number)
  return Date.UTC(y, m - 1, d) / 86_400_000
}

/** from에서 to까지 며칠인지. to가 앞이면 음수. */
export function daysBetween(from: string, to: string): number {
  return toUtcDay(to) - toUtcDay(from)
}

/** date에서 days일 뒤의 날("YYYY-MM-DD"). */
export function addDays(date: string, days: number): string {
  return new Date((toUtcDay(date) + days) * 86_400_000).toISOString().slice(0, 10)
}

/** 마감까지 남은 날의 글자: "D-3", 당일은 "D-Day". date가 today보다 앞이면 쓰지 않는다. */
export function formatDday(today: string, date: string): string {
  const left = daysBetween(today, date)
  return left === 0 ? "D-Day" : `D-${left}`
}

/** "2026년 11월 13일 금요일" */
export function formatLongDate(date: string): string {
  const [y, m, d] = date.split("-").map(Number)
  return `${y}년 ${m}월 ${d}일 ${WEEKDAYS[new Date(Date.UTC(y, m - 1, d)).getUTCDay()]}요일`
}

/** "2026-11-18 (수)" */
export function formatDateWeekday(date: string): string {
  const [y, m, d] = date.split("-").map(Number)
  return `${date} (${WEEKDAYS[new Date(Date.UTC(y, m - 1, d)).getUTCDay()]})`
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

/** 댓글 시각: 1분 안이면 "방금", 아니면 한국 날짜로 "11.05". */
export function formatCommentTime(createdAt: string, now: number = Date.now()): string {
  const at = Date.parse(createdAt)
  if (now - at < 60_000) return "방금"
  const parts = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", month: "2-digit", day: "2-digit" })
    .formatToParts(at)
  const pick = (type: string) => parts.find((p) => p.type === type)?.value ?? ""
  return `${pick("month")}.${pick("day")}`
}
