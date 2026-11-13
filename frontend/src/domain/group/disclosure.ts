import type { Disclosure } from "./types"

/** 공개 범위의 이름. */
export const DISCLOSURE_LABELS: Record<Disclosure, string> = {
  interest: "관심만",
  applied: "지원 여부까지",
  progress: "단계·결과까지",
}

/** 공개 범위를 고를 때 이름 아래 붙는 설명(시안 7차 "멤버", "창 · 공개 범위"). */
export const DISCLOSURE_DESCRIPTIONS: Record<Disclosure, string> = {
  interest: "관심을 켠 것만 보입니다.",
  applied: "관심과 지원함이 보입니다. 처음 값입니다.",
  progress: "서류·필기·면접 단계와 합격·불합격까지 보입니다.",
}

/** 고르는 순서. */
export const DISCLOSURES: Disclosure[] = ["interest", "applied", "progress"]
