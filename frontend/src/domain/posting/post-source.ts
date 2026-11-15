// 칸 확인의 "읽어 온 원문": 서버가 준 원문 글(줄바꿈이 든 글자)을 문단으로 나눈다(공고 문서 8절 17번).

/** 원문의 한 문단. heading은 묶음의 머리 줄(굵게 보인다)이다. */
export interface SourceParagraph {
  heading: string | null
  text: string
}

/** 묶음의 머리 줄(sections[].from)이 나오면 새 문단을 열고, 빈 줄에서 문단을 닫는다. 줄 앞뒤 공백은 지운다. */
export function sourceParagraphs(text: string, headings: (string | null)[]): SourceParagraph[] {
  const heads = new Set(headings.filter((h): h is string => !!h))
  const paragraphs: { heading: string | null; lines: string[] }[] = []
  let open = false
  for (const raw of text.split("\n")) {
    const line = raw.trim()
    if (!line) {
      open = false
    } else if (heads.has(line)) {
      paragraphs.push({ heading: line, lines: [] })
      open = true
    } else if (open) {
      paragraphs.at(-1)!.lines.push(line)
    } else {
      paragraphs.push({ heading: null, lines: [line] })
      open = true
    }
  }
  return paragraphs.map((p) => ({ heading: p.heading, text: p.lines.join("\n") }))
}
