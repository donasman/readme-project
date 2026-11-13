// 내 지원 상태와 내 공고 목록의 규칙. 화면과 떨어진 순수 함수다.
import { daysBetween, formatDday, formatMonthDayWeekday } from "@/shared/lib/date"

import type { Decision, MyPosting, MyState, Result, Stage } from "./types"

export const DECISION_LABELS: Record<Decision, string> = { undecided: "미정", hold: "보류", skip: "지원안함", applied: "지원함" }
/** 상태 바꾸기의 결정 순서(시안 9차 "지원 현황") */
export const DECISIONS: Decision[] = ["undecided", "hold", "skip", "applied"]
export const STAGE_LABELS: Record<Stage, string> = { document: "서류", test: "필기", interview_1: "1차 면접", interview_2: "2차 면접", final: "최종" }
export const STAGES: Stage[] = ["document", "test", "interview_1", "interview_2", "final"]
export const RESULT_LABELS: Record<Result, string> = { in_progress: "진행 중", passed: "합격", failed: "불합격" }
export const RESULTS: Result[] = ["in_progress", "passed", "failed"]

/** 지원함인데 단계·결과가 없으면 서류 · 진행 중으로 본다. */
function progress(state: MyState): { stage: Stage; result: Result } {
  return { stage: state.stage ?? "document", result: state.result ?? "in_progress" }
}

/** 단계와 결과 글자: "필기 단계", "서류 불합격", "최종 합격". */
function stageText(state: MyState): string {
  const { stage, result } = progress(state)
  return `${STAGE_LABELS[stage]} ${result === "in_progress" ? "단계" : RESULT_LABELS[result]}`
}

/** 목록의 짧은 상태 글자. 지원하고 서류 진행 중이면 "지원함", 그 뒤로는 단계 글자. */
export function statusLabel(state: MyState): string {
  if (state.decision !== "applied") return DECISION_LABELS[state.decision]
  const { stage, result } = progress(state)
  return stage === "document" && result === "in_progress" ? "지원함" : stageText(state)
}

/** 상세의 상태 글자: "지원함 · 필기 단계", "미정". */
export function statusDetail(state: MyState): string {
  return state.decision === "applied" ? `지원함 · ${stageText(state)}` : DECISION_LABELS[state.decision]
}

/** 마감 칸의 글자: 큰 글자(D-2, 상시, 확인 필요, 마감)와 작은 글자(11.15 (일), 날짜 없음, 마감일 모름). */
export function dueText(p: Pick<MyPosting, "deadline">, today: string): { main: string; sub: string; soon: boolean } {
  const d = p.deadline
  if (d.kind === "always") return { main: "상시", sub: "날짜 없음", soon: false }
  if (d.kind === "unknown") return { main: "확인 필요", sub: "마감일 모름", soon: false }
  const left = daysBetween(today, d.date)
  // 3일 안에 마감하면 진한 강조색으로 보인다(시안 8차 "홈"의 D-2, D-3)
  return { main: left < 0 ? "마감" : formatDday(today, d.date), sub: formatMonthDayWeekday(d.date), soon: left >= 0 && left <= 3 }
}

/** 강조색 알약으로 보일 상태: 지원했고 떨어지지 않았다. */
export const isActive = (state: MyState) => state.decision === "applied" && state.result !== "failed"

/** 마감됨: 마감일이 오늘보다 앞. 상시와 마감일 모름은 진행 중이다. */
export const isPostingClosed = (p: MyPosting, today: string) => p.deadline.kind === "date" && daysBetween(today, p.deadline.date) < 0

/** 진행 중 목록 순서: 마감일 가까운 것, 상시, 마감일 모름. 마감됨 목록은 최근 마감이 위. */
export function sortPostings(postings: MyPosting[], today: string): MyPosting[] {
  const rank = (p: MyPosting) => (p.deadline.kind === "date" ? 0 : p.deadline.kind === "always" ? 1 : 2)
  const date = (p: MyPosting) => (p.deadline.kind === "date" ? p.deadline.date : "")
  return [...postings].sort((a, b) => {
    const closed = Number(isPostingClosed(a, today)) - Number(isPostingClosed(b, today))
    if (closed) return closed
    if (isPostingClosed(a, today)) return date(b).localeCompare(date(a))
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
  const closedList = sorted.filter((p) => isPostingClosed(p, today))
  const openList = sorted.filter((p) => !isPostingClosed(p, today))
  const q = filters.query.trim().toLowerCase()
  const rows = (filters.view === "closed" ? closedList : openList).filter(
    (p) => (filters.decision === "all" || p.decision === filters.decision) && (!q || `${p.company} ${p.title}`.toLowerCase().includes(q))
  )
  return { rows, open: openList.length, closed: closedList.length }
}
