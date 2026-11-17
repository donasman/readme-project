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

/** 요일 한 글자("수"). "YYYY-MM-DD"를 시간대와 무관하게 센다. */
const weekday = (date: string): string => {
  const [y, m, d] = date.split("-").map(Number)
  return WEEKDAYS[new Date(Date.UTC(y, m - 1, d)).getUTCDay()]
}

/** "2026년 11월 13일 금요일" */
export function formatLongDate(date: string): string {
  const [y, m, d] = date.split("-").map(Number)
  return `${y}년 ${m}월 ${d}일 ${weekday(date)}요일`
}

/** "2026-11-18 (수)" */
export function formatDateWeekday(date: string): string {
  return `${date} (${weekday(date)})`
}

/** "11.05" */
export function formatMonthDay(date: string): string {
  const [, m, d] = date.split("-")
  return `${m}.${d}`
}

/** "11.16 (월)" */
export function formatMonthDayWeekday(date: string): string {
  return `${formatMonthDay(date)} (${weekday(date)})`
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

/** 한국의 오늘("YYYY-MM-DD"). 서버가 오늘을 주지 않는 화면(내 그룹)의 D-day를 센다. 서버도 한국 날짜로 센다(구조 문서 5.1). */
export const seoulToday = (now = new Date()): string => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(now)

/** 서버의 시각(+09:00이 붙은 ISO 8601)을 "YYYY-MM-DD HH:mm"으로. 앞부분이 이미 한국 날짜와 시각이라 잘라 쓴다. */
export const formatKstMinute = (iso: string): string => `${iso.slice(0, 10)} ${iso.slice(11, 16)}`
