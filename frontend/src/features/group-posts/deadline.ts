// 마감 규칙과 마감 칸의 글자. 화면과 떨어진 순수 함수다. 날짜는 "YYYY-MM-DD".
import { daysBetween, formatMonthDay, formatMonthDayWeekday } from "@/shared/lib/date"

import type { Deadline } from "./types"

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
