import { isHttpUrl } from "@/shared/lib/utils"

import type { Deadline, DraftField, PostUpload } from "./types"

/** 칸 확인 폼의 값(고친 글자 그대로). 공고 내용은 묶음마다 줄바꿈으로 나눈 글이다. */
export interface PostCheckValues {
  company: string
  title: string
  dueMode: Deadline["kind"]
  dueDate: string
  sourceUrl: string
  applyAt: string
  sections: { title: string; text: string }[]
  keywords: string[]
}

/** 보내기 전에 막는 칸과 그 글. 비어 있으면 보낸다. */
export function postCheckErrors(v: PostCheckValues): Partial<Record<DraftField, string>> {
  const errors: Partial<Record<DraftField, string>> = {}
  if (!v.company.trim()) errors.company = "회사명을 넣어 주세요."
  if (!v.title.trim()) errors.title = "공고명을 넣어 주세요."
  if (v.dueMode === "date" && !v.dueDate) errors.deadline = "마감일을 넣거나 상시, 모름을 고르세요."
  if (v.sourceUrl.trim() && !isHttpUrl(v.sourceUrl.trim())) errors.sourceUrl = "https:// 로 시작하는 주소를 넣거나 비워 두세요."
  return errors
}

/** 서버에 보내는 본문: 앞뒤 공백을 지우고, 빈 링크와 지원처는 null, 공고 내용은 빈 줄과 줄이 없는 묶음을 뺀다. */
export function toPostUpload(v: PostCheckValues): PostUpload {
  return {
    company: v.company.trim(),
    title: v.title.trim(),
    deadline: v.dueMode === "date" ? { kind: "date", date: v.dueDate } : { kind: v.dueMode },
    sourceUrl: v.sourceUrl.trim() || null,
    applyAt: v.applyAt.trim() || null,
    sections: v.sections
      .map((s) => ({ title: s.title, items: s.text.split("\n").map((l) => l.trim()).filter(Boolean) }))
      .filter((s) => s.items.length > 0),
    keywords: v.keywords,
  }
}
