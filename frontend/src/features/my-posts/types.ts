// 내 공고의 데이터 모양(관심을 켜거나 상태를 넣은 공고와 내 지원 상태). 서버의 응답(공고 문서 4.2) 그대로다.
// 서버는 상태 글자(status)도 주지만 화면은 domain/posting/my-state.ts의 statusLabel로 그린다(공고 문서 8절 14번).
import type { Disclosure } from "@/domain/group/types"
import type { MyState } from "@/domain/posting/my-state"
import type { Deadline, PostingAnalysis, PostSection } from "@/domain/posting/types"

import type { Stage } from "@/domain/posting/my-state"

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

/**
 * 요건 하나의 결과(공고 문서 4.8, 12차 시안 안 B): matched(카드 있음), profile(카드로는 판단할 수 없지만 프로필에 있음),
 * unmatched(카드 없음), unknown(판단 못 함). 프로필은 넣지 않아도 되는 값이라 없다고 unmatched가 되지 않는다.
 */
export interface RequirementResult {
  kind: "must" | "nice"
  text: string
  status: "matched" | "profile" | "unmatched" | "unknown"
  /** 맞춘 내 카드(matches의 순서). 모두 matches에도 있다 */
  cards: RequirementCard[]
  /** 요건 글에 있는 내 프로필 값(자격증, 어학 시험, 전공) */
  profileItems: string[]
}

/** 요건 하나를 맞춘 카드(공고 문서 4.8, 2026-11-19): overlap은 이 요건의 낱말 가운데 카드 태그(포함 관계를 더한 것)에 있는 것, 요건 글에 나온 순서. */
export interface RequirementCard {
  cardId: string
  overlap: string[]
}

/** 맞는 카드(공고 문서 4.8): 카드 제목, 경험 이름, 공고의 요건 낱말·키워드 가운데 카드 태그에 있는 것. 가짜 서버는 태그 겹침으로 흉내 낸다. */
export interface CardMatch {
  cardId: string
  title: string
  /** 경험 이름. 없으면 빈 글자 */
  experience: string
  overlap: string[]
}

/** 내 공고 상세. */
export interface MyPostingDetail extends MyPosting {
  applyAt: string | null
  sourceUrl: string | null
  sections: PostSection[]
  keywords: string[]
  /** 나만 보는 메모. 없으면 "" */
  memo: string
  /** 공고 분석. 없으면 서버가 맞는 카드를 세지 않아 matches와 unmatched가 비어 있다 */
  analysis: PostingAnalysis | null
  matches: CardMatch[]
  /** 카드로 답하지 못한 필수·우대 요건 */
  unmatched: string[]
  /** 필수·우대 요건마다의 결과(공고 문서 4.8). 분석이 없으면 비어 있다 */
  requirements: RequirementResult[]
  groupStates: PostingGroupState[]
}

/** 홈의 "그룹 소식" 한 줄: 내 그룹의 최근 7일 새 공고와 새 댓글(GET /api/me/group-activity, 공고 문서 4.5). */
export interface GroupNews {
  group: GroupLabel
  text: string
  /** 공고 하나에 대한 소식이면 그 공고 */
  postId: string | null
}

/** 홈에 모으는 것. 서버에서는 내 공고와 그룹 소식을 따로 받아 모은다(api.ts의 getHome). */
export interface HomeData {
  today: string
  postings: MyPosting[]
  unverifiedCards: number
  /** 메일에서 온 상태 변경(9차 메일 확인함)의 확인 필요 */
  mailChanges: { count: number; first: string | null }
  news: GroupNews[]
}

/** 내 공고 넣기 결과. 이미 내 공고에 있으면 서버가 409 ALREADY_IN_MY_POSTINGS와 그 공고의 id를 준다. */
export type AddPostingResult = { ok: true; postingId: string } | { ok: false; error: "ALREADY_IN_MY_POSTINGS"; postingId: string }

/** 내 공고 넣기가 이미 있는 공고의 상세로 보낼 때 넘기는 값. 상세가 "이미 내 공고에 있는 공고입니다"를 띄운다. */
export interface ArrivedState {
  arrived: true
}

/** 내 공고에서 뺀 뒤 목록으로 넘기는 값: 뺀 공고 이름과 관심이 꺼진 그룹. 목록 위에 알림으로 보인다. */
export interface RemovedState {
  removed: { name: string; groups: string[] }
}

/** 내 전형 일정 하나(GET /api/me/schedules, 메일 설계 4.5). 메일에서 반영하면 생긴다. */
export interface Schedule {
  postingId: string
  company: string
  title: string
  stage: Stage
  /** "YYYY-MM-DD" */
  date: string
  /** "HH:mm". 없으면 null */
  time: string | null
}
