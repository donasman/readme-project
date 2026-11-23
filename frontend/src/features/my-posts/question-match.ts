// 자기소개서 문항과 경험카드 매칭의 규칙(문항 매칭 문서): 유형 이름, 문항마다 카드 수, 더할 수 있는 카드, 문항 고치기 창의 검사.
import type { CardChoice, Question, QuestionType } from "./types"

/** 문항 유형의 이름(문항 매칭 리서치 3절) */
export const QUESTION_TYPES: Record<QuestionType, string> = {
  JOB: "직무역량",
  PROBLEM: "문제해결·개선",
  GOAL: "목표·도전",
  TEAM: "협업·소통",
  FAILURE: "실패·극복",
  GROWTH: "성장·자기개발",
  VALUES: "가치관·성격",
  MOTIVE: "지원동기",
  FUTURE: "입사 후 포부",
}

/** 회사와 직무 조사가 중심이라 카드가 없어도 되는 유형(문항 매칭 문서 4.3) */
export const isResearchType = (type: QuestionType | null): boolean => type === "MOTIVE" || type === "FUTURE"

/** 문항 수, 문항 글 길이, 글자 수의 끝(QD6). 서버도 같은 값으로 막는다 */
export const MAX_QUESTIONS = 10
export const QUESTION_TEXT_MAX = 1000
export const QUESTION_LIMIT_MAX = 10000

/** 문항에 둘 카드 수(QD1): 2장(주된 카드와 보조 카드), 글자 수가 500자 아래면 1장. */
export const maxCards = (limit: number | null): number => (limit !== null && limit < 500 ? 1 : 2)

/** 문항에 더할 수 있는 내 카드: 그 문항에 없는 카드. 문항의 카드가 꽉 찼으면 없다. 다른 문항의 카드는 막지 않는다. */
export function addableQuestionCards(choices: CardChoice[], question: Pick<Question, "limit" | "cards">): CardChoice[] {
  if (question.cards.length >= maxCards(question.limit)) return []
  return choices.filter((c) => !question.cards.some((q) => q.cardId === c.cardId))
}

/** 창의 글자 수 칸 값을 숫자로. 비었으면 null(글자 수 없음) */
export const toLimit = (value: string): number | null => (value.trim() === "" ? null : Number(value))

/** 글자 수 칸의 오류 글. 비었거나 1~10,000의 정수면 null */
export function limitError(value: string): string | null {
  const limit = toLimit(value)
  return limit === null || (/^\d+$/.test(value.trim()) && limit >= 1 && limit <= QUESTION_LIMIT_MAX) ? null : "글자 수는 1~10,000 사이 숫자로 넣거나 비워 두세요."
}

/** 문항 글 칸의 오류 글. 길이는 칸의 maxLength가 막는다 */
export const textError = (text: string): string | null => (text.trim() === "" ? "문항 글을 넣어 주세요." : null)
