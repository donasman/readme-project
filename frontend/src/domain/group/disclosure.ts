import type { Disclosure } from "./types"

/** 공개 범위의 이름. */
export const DISCLOSURE_LABELS: Record<Disclosure, string> = {
  interest: "관심만",
  applied: "지원 여부까지",
  progress: "단계·결과까지",
}
