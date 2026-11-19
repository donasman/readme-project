// 경험카드의 데이터 모양. 서버의 응답(경험카드 문서 4.1, 4.3) 그대로다. id는 shared/lib/api.ts가 문자열로 읽는다.
// 경험카드 화면과 내 공고 상세(카드 미리 보기 창)가 함께 쓴다.

/** 카드로 답할 수 있는 자소서 문항 유형. 값은 한글 그대로다(설계 문서 3.2). */
export type QuestionType = "지원동기" | "직무역량" | "협업" | "문제해결" | "성장" | "기타"

/** 경험카드 한 장(목록에 보이는 칸). */
export interface ExperienceCard {
  id: string
  /** 경험 이름(경험카드 문서 4.8). 같은 이름의 카드가 한 경험의 장면들이다. 없으면 빈 글자 */
  experience: string
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
  /** 뽑은 카드도 직접 만든 카드도 사람이 확인하기 전까지 false */
  verified: boolean
}

/** STAR 네 칸과 어려움·배운 점(경험카드 문서 4.8). 빈 칸은 빈 글자다. 어려움과 배운 점은 선택이다. */
export interface Star {
  situation: string
  task: string
  action: string
  result: string
  obstacle: string
  learning: string
}

/** 카드 검사 결과 한 줄(설계 문서의 issues): 오류·경고, 어느 칸, 글. */
export interface CardIssue {
  level: "오류" | "경고"
  field: string
  message: string
}

/** 카드 상세(GET /api/cards/{id}). 목록 칸에 STAR, 근거 숫자, 검사 결과, 추출 메모, 자료가 더해진다. */
export interface CardDetail extends ExperienceCard {
  star: Star
  /** 근거 숫자. 확인하면 결과에만 있는 숫자가 여기로 옮겨진다 */
  facts: string[]
  /** 지금은 나만 보기 하나다(설계 가정 9) */
  visibility: "PRIVATE"
  sourceId: string | null
  /** 카드를 뽑은 자료의 파일 이름. 직접 만든 카드, 자료를 지운 카드, 붙여넣은 글이면 null */
  sourceName: string | null
  /** 카드가 나온 자료 조각(5,000자까지). 문단은 줄바꿈으로 나눈다. 직접 만든 카드, 자료를 지운 카드, 추출 전에 만든 카드는 null */
  sourceExcerpt: string | null
  extractionNotes: string[]
  issues: CardIssue[]
}
