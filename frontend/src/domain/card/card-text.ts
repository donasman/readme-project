// 추출이 남긴 "[확인 필요: …]" 표시를 읽는 순수 함수. 카드 화면과 카드 미리 보기 창이 함께 쓴다.

/** 추출이 원문에 없는 숫자를 바꿔 넣은 표시. 예: "[확인 필요: 30%]" */
export const FLAG = /\[확인 필요: ([^\]]+)\]/g

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
