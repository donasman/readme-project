// 내 공고의 데이터 모양(관심을 켜거나 직접 넣은 공고와 내 지원 상태). 상태 값의 글자는 정했다: 소문자로 주고받는다(그룹 설계 4.3 아래 "정함").
// shortcut: 내 공고 조회와 메모가 설계에 없어 모양은 프런트가 정했다, 설계가 생기면 맞춘다(화면 API 대응 3.3의 10, 12번)
import type { Disclosure } from "@/domain/group/types"
import type { MyState } from "@/domain/posting/my-state"
import type { Deadline, PostSection } from "@/domain/posting/types"

export type { Decision, MyState, Result, Stage } from "@/domain/posting/my-state"

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

// shortcut: 맞추기 규칙이 없어 가짜 서버가 태그 겹침으로 흉내 낸다, 매칭이 설계되면 맞춘다(화면 API 대응 3.3의 13번)
/** 맞는 카드: 카드 제목과 겹치는 태그·키워드. */
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

// shortcut: 무엇을 소식으로 볼지 정하지 않아 예시만 보인다, 정해지면 맞춘다(화면 API 대응 3.3의 14번)
/** 홈의 "그룹 소식" 한 줄. */
export interface GroupNews {
  group: GroupLabel
  text: string
  /** 공고 하나에 대한 소식이면 그 공고 */
  postId: string | null
}

// shortcut: 홈 전용 API 하나로 받는다고 보고 짰다, 화면이 여러 API를 부르기로 하면 바꾼다(화면 API 대응 3.3의 14번)
/** 홈에 모으는 것. */
export interface HomeData {
  today: string
  postings: MyPosting[]
  unverifiedCards: number
  /** 메일에서 온 상태 변경(9차 메일 확인함)의 확인 필요 */
  mailChanges: { count: number; first: string | null }
  news: GroupNews[]
}
