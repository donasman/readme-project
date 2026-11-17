// 카드 글을 다루는 순수 함수: 추출이 남긴 "[확인 필요: …]" 표시, 자료 원문의 숫자, 자료를 나눠 읽는 조각 수.
import type { Star } from "./types"

/** 추출이 원문에 없는 숫자를 바꿔 넣은 표시. 예: "[확인 필요: 30%]" */
const FLAG = /\[확인 필요: ([^\]]+)\]/g

export type TextPart = { text: string; flagged?: boolean }

/** 글을 보통 글자와 "확인 필요" 표시로 나눈다. 표시 부분의 text는 "확인 필요: 30%"다. */
export function splitFlagged(text: string): TextPart[] {
  const parts: TextPart[] = []
  let last = 0
  for (const m of text.matchAll(FLAG)) {
    if (m.index > last) parts.push({ text: text.slice(last, m.index) })
    parts.push({ text: `확인 필요: ${m[1]}`, flagged: true })
    last = m.index + m[0].length
  }
  if (last < text.length) parts.push({ text: text.slice(last) })
  return parts
}

/** 글에 남은 "확인 필요" 숫자들. 예: ["30%"] */
export function flaggedValues(text: string): string[] {
  return [...text.matchAll(FLAG)].map((m) => m[1])
}

/**
 * "확인 필요" 표시가 든 문장을 지운다(카드 확인의 "이 문장 지우기").
 * 문장은 마침표·물음표·느낌표 뒤의 빈칸에서 나눈다(가짜 서버의 moveStarNumbers와 같은 규칙). 1.5 같은 소수점에서는 끊지 않는다.
 */
export function dropFlaggedSentences(text: string): string {
  return text
    .split(/(?<=[.?!])\s+/)
    .filter((s) => !new RegExp(FLAG.source).test(s))
    .join(" ")
    .trim()
}

/** 이야기 여섯 칸에 남은 "확인 필요" 숫자들. 서버는 어느 칸에나 붙인다(경험카드 문서 4.8) */
export const storyFlaggedValues = (star: Star): string[] => Object.values(star).flatMap(flaggedValues)

/** 이야기 여섯 칸에서 "확인 필요" 표시가 든 문장을 지운다. 표시가 없는 칸은 줄바꿈까지 그대로 둔다 */
export function dropFlaggedStory(star: Star): Star {
  const drop = (text: string) => (flaggedValues(text).length ? dropFlaggedSentences(text) : text)
  return { situation: drop(star.situation), task: drop(star.task), obstacle: drop(star.obstacle), action: drop(star.action), result: drop(star.result), learning: drop(star.learning) }
}

/** 원문 문단을 나누고, 카드 글(usedText)에 쓰인 숫자를 굵게 표시할 수 있게 나눈다. */
export function markUsedNumbers(excerpt: string, usedText: string): TextPart[][] {
  // 글자나 + 바로 뒤의 숫자(p95, N+1)와 "1건당"처럼 단위로 쓴 수는 숫자로 보지 않는다.
  // 뒤 확인은 단위를 떼고 다시 맞추는 것(1건당 -> 1, 1.8초 -> 1)도 막는다.
  const NUM = /(?<![A-Za-z+\d.])\d+(?:[.,]\d+)?(?:초|회|%|건|명|배|분|개)?(?![초회%건명배분개당\d]|[.,]\d)/g
  const used = new Set(usedText.match(NUM) ?? [])
  return excerpt
    .split("\n")
    .filter((p) => p.trim())
    .map((paragraph) => {
      const parts: TextPart[] = []
      let last = 0
      for (const m of paragraph.matchAll(NUM)) {
        if (!used.has(m[0])) continue
        if (m.index > last) parts.push({ text: paragraph.slice(last, m.index) })
        parts.push({ text: m[0], flagged: true })
        last = m.index + m[0].length
      }
      if (last < paragraph.length) parts.push({ text: paragraph.slice(last) })
      return parts
    })
}

/** 자료를 나눠 읽는 조각 크기와 조각 수. 조각 하나가 AI 호출 한 번이다. */
export const CHUNK_SIZE = 5000
export const chunkCount = (chars: number): number => Math.max(1, Math.ceil(chars / CHUNK_SIZE))

/** 요약 글자 수 한도(설계 문서의 카드 검사: 60자 초과 경고). */
export const SUMMARY_LIMIT = 60

/** 화면에 쓰는 글자 수: 1,234자 */
export const formatChars = (n: number): string => `${n.toLocaleString("ko-KR")}자`
