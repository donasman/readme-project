// 경험카드의 데이터 모양. docs/리드미_설계_로그인_경험카드.md 4.2절의 카드 응답에서 목록에 쓰는 칸만 둔다.
// 서버가 생기면 응답이 이 모양으로 온다고 보고 화면을 짠다.

/** 카드로 답할 수 있는 자소서 문항 유형. 값은 한글 그대로다(설계 문서 3.2). */
export type QuestionType = "지원동기" | "직무역량" | "협업" | "문제해결" | "성장" | "기타"

/** 경험카드 한 장(목록에 보이는 칸). */
export interface ExperienceCard {
  id: number
  title: string
  /** 기간. 글자 그대로("2025-03 ~ 2025-05", "2026 ~ 현재") */
  period: string | null
  /** 어디서: 회사, 프로젝트, 스터디 */
  org: string | null
  /** 맡은 일 */
  role: string | null
  tags: string[]
  summary: string
  usableFor: QuestionType[]
  /** 자료에서 뽑은 카드는 사람이 확인하기 전까지 false */
  verified: boolean
}

/** 내 카드에 쓰인 태그와 그 태그가 붙은 카드 수(GET /api/cards/tags). */
export interface CardTag {
  name: string
  count: number
}

/** 경험카드 목록 화면의 loader가 받는 것. */
export interface CardsData {
  cards: ExperienceCard[]
  tags: CardTag[]
}
