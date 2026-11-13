// 내 지원 상태(결정·단계·결과)와 그 글자. 내 공고와 메일 확인함이 함께 쓴다. 화면과 떨어진 순수 함수다.
// 값의 글자는 정했다: 소문자로 주고받는다(그룹 설계 4.3 아래 "정함").

/** 결정: 미정, 보류, 지원안함, 지원함. */
export type Decision = "undecided" | "hold" | "skip" | "applied"
/** 단계: 서류, 필기, 1차 면접, 2차 면접, 최종. 지원함일 때만 있다. */
export type Stage = "document" | "test" | "interview_1" | "interview_2" | "final"
/** 지금 단계의 결과: 진행 중, 합격, 불합격. 지원함일 때만 있다. */
export type Result = "in_progress" | "passed" | "failed"

/** 내 지원 상태(PUT /api/me/postings/{postingId}의 본문). 단계와 결과는 applied일 때만 함께 넣는다. */
export interface MyState {
  decision: Decision
  stage: Stage | null
  result: Result | null
}

export const DECISION_LABELS: Record<Decision, string> = { undecided: "미정", hold: "보류", skip: "지원안함", applied: "지원함" }
/** 상태 바꾸기의 결정 순서(시안 8차 "지원 현황") */
export const DECISIONS: Decision[] = ["undecided", "hold", "skip", "applied"]
export const STAGE_LABELS: Record<Stage, string> = { document: "서류", test: "필기", interview_1: "1차 면접", interview_2: "2차 면접", final: "최종" }
export const STAGES: Stage[] = ["document", "test", "interview_1", "interview_2", "final"]
export const RESULT_LABELS: Record<Result, string> = { in_progress: "진행 중", passed: "합격", failed: "불합격" }
export const RESULTS: Result[] = ["in_progress", "passed", "failed"]

/** 지원함인데 단계·결과가 없으면 서류 · 진행 중으로 본다. */
export function progress(state: MyState): { stage: Stage; result: Result } {
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

/** 강조색 알약으로 보일 상태: 지원했고 떨어지지 않았다. */
export const isActive = (state: MyState) => state.decision === "applied" && state.result !== "failed"
