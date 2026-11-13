// 그룹 공고의 데이터 모양. 서버가 생기면 응답이 이 모양으로 온다고 보고 화면을 짠다.
// 날짜는 모두 한국 시간 기준 "YYYY-MM-DD"(댓글만 시각까지 ISO 8601).
// 그룹과 멤버는 domain/group, 보는 사람은 domain/session에 있다.
import type { Member } from "@/domain/group/types"

/** 마감. 날짜를 못 읽었으면 unknown, 상시·수시 채용이면 always. */
export type Deadline =
  | { kind: "date"; date: string }
  | { kind: "always" }
  | { kind: "unknown" }

/**
 * 그룹에 보이는 다른 멤버의 관심. 지원 정보는 그 멤버가 이 그룹에 정한 공개 범위만큼만 들어 있다
 * ("지원 여부까지" 이상이면 applied, "단계·결과까지"면 progress도).
 */
export interface Interest {
  member: Member
  applied: boolean
  /** 예: "면접 단계" */
  progress?: string
}

/** 보는 사람(나)의 이 공고 상태. */
export interface MyState {
  /** 관심 켜기·끄기 */
  interested: boolean
  /** 내 지원 관리의 상태 값(지원함, 필기 단계 …). 있으면 관심 켜기·끄기 대신 이 값을 보인다 */
  status: string | null
}

/** 그룹 공고 목록의 한 줄. */
export interface GroupPost {
  id: string
  groupId: string
  company: string
  title: string
  deadline: Deadline
  postedBy: Member
  postedAt: string
  /** 관심을 켠 다른 멤버들(보는 사람 제외) */
  interests: Interest[]
  me: MyState
  commentCount: number
}

/** 댓글 본문은 글자와 링크만 받는다. */
export type CommentPart = { text: string; href?: string }

/** "그룹 × 공고"에 달린 댓글. */
export interface Comment {
  id: string
  author: Member
  createdAt: string
  body: CommentPart[]
}

/** 공고 내용의 한 묶음(주요 업무, 필수 요건 …). */
export interface PostSection {
  title: string
  items: string[]
}

/** 그룹 공고 상세. 목록의 한 줄에 공고 내용과 댓글이 더해진다. */
export interface GroupPostDetail extends GroupPost {
  /** 마감의 이름. 예: "서류 접수 마감" */
  deadlineName: string | null
  /** 지원처. 예: "회사 채용 홈페이지" */
  applyAt: string | null
  sourceUrl: string | null
  sections: PostSection[]
  keywords: string[]
  comments: Comment[]
}

/** 목록 화면의 loader가 받는 것. today는 서버 기준 오늘이다(D-day와 "새 공고"를 이 날로 센다). */
export interface GroupPostsData {
  today: string
  posts: GroupPost[]
}

/** 상세 화면의 loader가 받는 것. */
export interface GroupPostData {
  today: string
  post: GroupPostDetail
}
