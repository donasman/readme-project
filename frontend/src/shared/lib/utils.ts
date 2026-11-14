export { cn } from "cn"

/**
 * 클래스를 지우지 않고 잇는다. cn은 겹치는 Tailwind 클래스를 지우는데, 리드미 토큰 이름(text-label-sm 등)은
 * 글자 색으로 읽혀 뒤의 text-색 클래스와 함께 있으면 지워진다. 공용 부품의 기본 클래스는 이것으로 잇는다.
 */
export function joinClasses(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(" ")
}

/** http·https 주소인가. 다른 사람이 누를 링크로 보여 줄 값(원문 링크 등)을 받을 때 쓴다. */
export function isHttpUrl(value: string): boolean {
  try {
    return ["http:", "https:"].includes(new URL(value).protocol)
  } catch {
    return false
  }
}

/**
 * 파일 이름이 input의 accept(".pdf,.docx" 같은 확장자 목록)에 맞는가.
 * accept는 고르기 창만 거르므로, 끌어 놓은 파일과 "모든 파일"로 고른 파일은 이것으로 본다.
 */
export function matchesAccept(fileName: string, accept: string): boolean {
  const name = fileName.toLowerCase()
  return accept.split(",").some((ext) => name.endsWith(ext.trim().toLowerCase()))
}

// Intl.Segmenter가 없는 옛 브라우저(Firefox 125 전)에서도 이 모듈을 읽다 앱 전체가 멈추지 않게 있을 때만 만든다
const graphemes = typeof Intl.Segmenter === "function" ? new Intl.Segmenter() : null

/** 첫 글자 하나(이름·그룹 이름의 동그라미). 이모지와 조합형 한글도 반쪽으로 자르지 않는다. */
export function firstChar(text: string): string {
  if (!graphemes) return Array.from(text)[0] ?? ""
  for (const { segment } of graphemes.segment(text)) return segment
  return ""
}
