// 한국어 글자 다루기. 순수 함수.

/** 마지막 글자가 한글이면 받침이 있는지 돌려준다. 한글이 아니면 null. */
function hasFinalConsonant(word: string): boolean | null {
  const code = word.charCodeAt(word.length - 1) - 0xac00
  if (!(code >= 0 && code <= 11171)) return null
  return code % 28 !== 0
}

/** 이름 뒤 주격 조사: 받침이 있으면 "이", 없으면 "가". 한글로 끝나지 않으면 "이(가)". */
export function subjectParticle(word: string): string {
  const final = hasFinalConsonant(word)
  return final === null ? "이(가)" : final ? "이" : "가"
}

/** 이름 뒤 목적격 조사: 받침이 있으면 "을", 없으면 "를". 한글로 끝나지 않으면 "을(를)". */
export function objectParticle(word: string): string {
  const final = hasFinalConsonant(word)
  return final === null ? "을(를)" : final ? "을" : "를"
}

/** 마침표 없이 끝난 글(서버의 오류 글)을 문장으로 끝낸다. 칸 아래 다른 글처럼 보이게 한다. */
export const sentence = (text: string) => (/[.?!]$/.test(text) ? text : `${text}.`)
