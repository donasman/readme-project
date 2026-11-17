// 공고 분석을 화면의 "요약"으로 보이는 규칙(12차 시안). 내 공고 상세와 그룹 공고 상세가 함께 쓴다.
import type { PostingAnalysis, PostSection } from "./types"

/** 공고 내용을 보는 방식: 분석이 있으면 요약이 먼저다. */
export type ContentView = "summary" | "source"
export const CONTENT_VIEWS: { value: ContentView; label: string }[] = [
  { value: "summary", label: "요약" },
  { value: "source", label: "원문" },
]

/** 요약의 묶음: 주요 업무, 필수 역량, 우대 역량. 빈 묶음은 뺀다. 원문의 묶음과 같은 모양이라 같은 목록으로 그린다. */
export const summarySections = (analysis: PostingAnalysis): PostSection[] =>
  [
    { title: "주요 업무", items: analysis.duties },
    { title: "필수 역량", items: analysis.must },
    { title: "우대 역량", items: analysis.nice },
  ].filter((s) => s.items.length > 0)

/** 자기소개서 문항의 글자 수: "1,000자", 공고에 없으면 "글자 수 없음". */
export const formatQuestionLimit = (limit: number | null): string => (limit === null ? "글자 수 없음" : `${limit.toLocaleString("ko-KR")}자`)
