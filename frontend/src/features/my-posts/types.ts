// 내 공고의 데이터 모양(관심을 켜거나 직접 넣은 공고와 내 지원 상태). 서버의 내 공고 조회는 아직 설계에 없다
// (화면 API 대응 3.3의 10번). 상태 값의 글자는 정했다: 소문자로 주고받는다(그룹 설계 4.3 아래 "정함").
import type { Disclosure } from "@/domain/group/types"
import type { Deadline, PostSection } from "@/domain/posting/types"

/** 결정: 미정, 보류, 지원안함, 지원함. */
export type Decision = "undecided" | "hold" | "skip" | "applied"
/** 단계: 서류, 필기, 1차 면접, 2차 면접, 최종. 지원함일 때만 있다. */
export type Stage = "document" | "test" | "interview_1" | "interview_2" | "final"
/** 지금 단계의 결과: 진행 중, 합격, 불합격. 지원함일 때만 있다. */
export type Result = "in_progress" | "passed" | "failed"

/** 내 지원 상태(PUT /api/me/postings/{postingId}의 본문). 단계와 결과는 applied일 때만 함께 넣는다. */
export interface MyState {
  decision: Decision
  stage: Stage | null
  result: Result | null
}

/** 그룹의 이름과 표시 글자. */
export interface GroupLabel {
  id: string
  name: string
  mark: string
}

/** 이 공고가 올라간 내 그룹과 그 그룹에서의 공고 id(그룹 공고 상세로 간다). */
export interface PostingGroup extends GroupLabel {
  postId: string
}

/** 내 공고 목록의 한 줄. */
export interface MyPosting extends MyState {
  id: string
  company: string
  title: string
  deadline: Deadline
  groups: PostingGroup[]
}

/** 이 공고가 있는 그룹에서의 내 모습: 관심, 댓글 수, 내 공개 범위. */
export interface PostingGroupState extends PostingGroup {
  interested: boolean
  commentCount: number
  disclosure: Disclosure
}

/** 맞는 카드: 카드 제목과 겹치는 태그·키워드. 맞추기 규칙은 아직 없다(화면 API 대응 3.3의 13번). */
export interface CardMatch {
  cardId: number
  title: string
  overlap: string[]
}

/** 내 공고 상세. */
export interface MyPostingDetail extends MyPosting {
  applyAt: string | null
  sourceUrl: string | null
  sections: PostSection[]
  keywords: string[]
  /** 나만 보는 메모(화면 API 대응 3.3의 12번) */
  memo: string
  matches: CardMatch[]
  /** 카드로 답하지 못한 요건 */
  unmatched: string[]
  groupStates: PostingGroupState[]
}

/** 홈의 "그룹 소식" 한 줄. 무엇을 소식으로 볼지는 아직 정하지 않았다(화면 API 대응 3.3의 14번). */
export interface GroupNews {
  group: GroupLabel
  text: string
  /** 공고 하나에 대한 소식이면 그 공고 */
  postId: string | null
}

/** 홈에 모으는 것. 홈 전용 API를 둘지 화면이 여러 API를 부를지는 아직 정하지 않았다(3.3의 14번). */
export interface HomeData {
  today: string
  postings: MyPosting[]
  unverifiedCards: number
  /** 메일에서 온 상태 변경(9차 메일 확인함)의 확인 필요 */
  mailChanges: { count: number; first: string | null }
  news: GroupNews[]
}
