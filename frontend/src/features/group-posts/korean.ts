// 한국어 글자 다루기. 순수 함수.

/** 이름 뒤 주격 조사: 받침이 있으면 "이", 없으면 "가". 한글로 끝나지 않으면 "이(가)". */
export function subjectParticle(word: string): string {
  const code = word.charCodeAt(word.length - 1) - 0xac00
  if (code < 0 || code > 11171) return "이(가)"
  return code % 28 === 0 ? "가" : "이"
}
