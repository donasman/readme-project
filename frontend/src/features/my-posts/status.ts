// 내 지원 상태와 내 공고 목록의 규칙. 화면과 떨어진 순수 함수다.
import { daysBetween, formatDday, formatMonthDayWeekday } from "@/shared/lib/date"

import { isClosed } from "@/domain/posting/deadline"
import { progress, STAGES, type Decision, type MyState } from "@/domain/posting/my-state"

import type { MyPosting } from "./types"

/** 마감 칸의 글자: 큰 글자(D-2, 상시, 확인 필요, 마감)와 작은 글자(11.15 (일), 날짜 없음, 마감일 모름). */
export function dueText(p: Pick<MyPosting, "deadline">, today: string): { main: string; sub: string; soon: boolean } {
  const d = p.deadline
  if (d.kind === "always") return { main: "상시", sub: "날짜 없음", soon: false }
  if (d.kind === "unknown") return { main: "확인 필요", sub: "마감일 모름", soon: false }
  const left = daysBetween(today, d.date)
  // 3일 안에 마감하면 진한 강조색으로 보인다(시안 8차 "홈"의 D-2, D-3)
  return { main: left < 0 ? "마감" : formatDday(today, d.date), sub: formatMonthDayWeekday(d.date), soon: left >= 0 && left <= 3 }
}

/** 진행 중 목록 순서: 마감일 가까운 것, 상시, 마감일 모름. 마감됨 목록은 최근 마감이 위. */
export function sortPostings(postings: MyPosting[], today: string): MyPosting[] {
  const rank = (p: MyPosting) => (p.deadline.kind === "date" ? 0 : p.deadline.kind === "always" ? 1 : 2)
  const date = (p: MyPosting) => (p.deadline.kind === "date" ? p.deadline.date : "")
  return [...postings].sort((a, b) => {
    const closed = Number(isClosed(a.deadline, today)) - Number(isClosed(b.deadline, today))
    if (closed) return closed
    if (isClosed(a.deadline, today)) return date(b).localeCompare(date(a))
    return rank(a) - rank(b) || date(a).localeCompare(date(b))
  })
}

/** 지원 현황의 순서: 떨어진 공고는 아래, 단계가 앞선 공고가 위, 같으면 마감일 가까운 순. */
export function sortBoard(postings: MyPosting[]): MyPosting[] {
  const date = (p: MyPosting) => (p.deadline.kind === "date" ? p.deadline.date : "9999")
  return postings
    .filter((p) => p.decision === "applied")
    .sort((a, b) => {
      const failed = Number(a.result === "failed") - Number(b.result === "failed")
      if (failed) return failed
      return STAGES.indexOf(progress(b).stage) - STAGES.indexOf(progress(a).stage) || date(a).localeCompare(date(b))
    })
}

/** 지원 현황 한 줄의 단계 칸: 지난 단계는 통과, 지금 단계는 결과, 뒤 단계는 비움. */
export function stageCells(state: MyState): ("pass" | "now" | "fail" | "none")[] {
  const { stage, result } = progress(state)
  const at = STAGES.indexOf(stage)
  return STAGES.map((_, i) => (i < at ? "pass" : i > at ? "none" : result === "passed" ? "pass" : result === "failed" ? "fail" : "now"))
}

/** 상태 바꾸기에서 결정을 고르면: 지원함이면 단계·결과를 채우고(없으면 서류 · 진행 중), 아니면 비운다. */
export function withDecision(state: MyState, decision: Decision): MyState {
  if (decision !== "applied") return { decision, stage: null, result: null }
  return { decision, ...progress(state) }
}

/** 내 공고 거르기: 진행 중·마감됨, 결정, 검색어(회사명, 공고명). */
export interface MyFilters {
  view: "open" | "closed"
  decision: Decision | "all"
  query: string
}
export const DEFAULT_MY_FILTERS: MyFilters = { view: "open", decision: "all", query: "" }

/** 거른 목록과 진행 중·마감됨의 수(결정과 검색어로 거른 뒤의 수가 아니라 전체 수). */
export function filterPostings(postings: MyPosting[], filters: MyFilters, today: string): { rows: MyPosting[]; open: number; closed: number } {
  const sorted = sortPostings(postings, today)
  const closedList = sorted.filter((p) => isClosed(p.deadline, today))
  const openList = sorted.filter((p) => !isClosed(p.deadline, today))
  const q = filters.query.trim().toLowerCase()
  const rows = (filters.view === "closed" ? closedList : openList).filter(
    (p) => (filters.decision === "all" || p.decision === filters.decision) && (!q || `${p.company} ${p.title}`.toLowerCase().includes(q))
  )
  return { rows, open: openList.length, closed: closedList.length }
}

/**
 * 지원 현황의 숫자 넷. 지원함 = 진행 중 + 최종 합격 + 불합격이 늘 맞는다.
 * 최종 단계의 합격은 최종 합격, 어느 단계든 불합격은 불합격, 나머지 지원함(최종이 아닌 단계의 합격 포함)은 진행 중이다.
 */
export function boardCounts(postings: MyState[]): { applied: number; active: number; passed: number; failed: number } {
  const applied = postings.filter((p) => p.decision === "applied")
  const passed = applied.filter((p) => p.stage === "final" && p.result === "passed").length
  const failed = applied.filter((p) => p.result === "failed").length
  return { applied: applied.length, active: applied.length - passed - failed, passed, failed }
}
