// 그룹 공고의 데이터 모양. 서버의 응답(공고 문서 4.4) 그대로다. id는 shared/lib/api.ts가 문자열로 읽는다.
// 날짜는 모두 한국 시간 기준 "YYYY-MM-DD"(댓글만 시각까지 ISO 8601).
// 그룹과 멤버는 domain/group, 보는 사람은 domain/session에 있다.
import type { Member } from "@/domain/group/types"
import type { Deadline, PostSection } from "@/domain/posting/types"

export type { Deadline, PostSection }

/**
 * 그룹에 보이는 다른 멤버의 관심. 지원 정보는 그 멤버가 이 그룹에 정한 공개 범위만큼만 들어 있다
 * ("지원 여부까지" 이상이면 applied, "단계·결과까지"면 progress도).
 */
export interface Interest {
  member: Member
  applied: boolean
  /** 서버의 상태 글자. 예: "1차 면접 단계" */
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
  /** 탈퇴한 사람이면 null */
  postedBy: Member | null
  postedAt: string
  /** 관심을 켠 다른 멤버들(보는 사람 제외) */
  interests: Interest[]
  me: MyState
  commentCount: number
  /** 원문 주소. 링크 없이 본문을 붙여넣은 공고는 null */
  sourceUrl: string | null
}

/** 댓글 본문은 글자와 링크만 받는다. */
export type CommentPart = { text: string; href?: string }

/** "그룹 × 공고"에 달린 댓글. */
export interface Comment {
  id: string
  /** 탈퇴한 사람이면 null */
  author: Member | null
  /** +09:00이 붙은 ISO 8601. 서버가 만든 것은 소수 초가 붙을 수 있다 */
  createdAt: string
  body: CommentPart[]
}

/** 목록에서 상세로 갈 때 넘기는 값: 목록의 ? 뒤(고른 필터). 상세의 "공고 목록"이 그 목록으로 돌아간다. */
export interface ListReturnState {
  listSearch: string
}

/** 그룹 공고 상세. 목록의 한 줄에 공고 내용과 댓글이 더해진다. */
export interface GroupPostDetail extends GroupPost {
  /** 마감의 이름. 예: "서류 접수 마감" */
  deadlineName: string | null
  /** 지원처. 예: "회사 채용 홈페이지" */
  applyAt: string | null
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

// ---- 공고 올리기(시안 7차 "공고 올리기", "칸 확인") ----
// 읽어 오기와 칸 확인은 내 공고 넣기와 함께 쓰므로 domain/posting에 있다. 여기는 그룹에 올린 결과만 둔다.

/** 올리기 결과. 같은 공고가 이 그룹에 있으면 서버가 409 ALREADY_IN_GROUP과 그 공고를 알려 준다. */
export type UploadPostResult = { ok: true; postId: string } | { ok: false; error: "ALREADY_IN_GROUP"; postId: string }

// ---- 공고 고치기(시안 10차 "창 · 공고 고치기") ----

/** 고칠 수 있는 값: 회사명, 공고명, 마감. 원문 링크와 공고 내용은 고치지 않는다. */
export interface PostEdit {
  company: string
  title: string
  deadline: Deadline
}

/** 고치기 결과. 다른 사람이 다른 그룹에도 올린 공고면 서버가 409 POSTING_SHARED와 그 까닭을 알려 준다. */
export type EditPostResult = { ok: true } | { ok: false; error: "POSTING_SHARED"; message: string }
