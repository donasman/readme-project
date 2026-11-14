// 그룹 공고의 데이터 모양. 서버가 생기면 응답이 이 모양으로 온다고 보고 화면을 짠다.
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

/** 그룹 공고 상세. 목록의 한 줄에 공고 내용과 댓글이 더해진다. */
/** 목록에서 상세로 갈 때 넘기는 값: 목록의 ? 뒤(고른 필터). 상세의 "공고 목록"이 그 목록으로 돌아간다. */
export interface ListReturnState {
  listSearch: string
}

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

// ---- 공고 올리기(시안 7차 "공고 올리기", "칸 확인") ----
// shortcut: 읽어 오기의 모양은 프런트가 정했다, 설계가 생기면 맞춘다(화면 API 대응 3.2의 7~9번)

/** 공고를 넣는 방법: 링크, 붙여넣은 본문(원문 링크는 비워도 된다), 파일. */
export type PostReadInput =
  | { kind: "url"; url: string }
  | { kind: "text"; text: string; sourceUrl: string }
  | { kind: "file"; file: File }

/** 칸 확인 화면의 칸 가운데 근거와 "읽지 못함"을 따로 보이는 칸. */
export type DraftField = "company" | "title" | "deadline" | "sourceUrl" | "applyAt"

/** 공고 내용 한 묶음과 그 근거(원문의 어느 머리 아래에서 읽었는지). */
export interface DraftSection extends PostSection {
  from: string | null
}

/** 읽어 온 공고. 칸 확인 화면이 고친 뒤 올린다. */
export interface PostDraft {
  company: string
  title: string
  deadline: Deadline
  sourceUrl: string
  applyAt: string
  sections: DraftSection[]
  keywords: string[]
  /** 칸마다 읽어 온 근거 */
  evidence: Partial<Record<DraftField, string>>
  /** 읽지 못한 칸(노란 칸) */
  unread: DraftField[]
  /** 오른쪽에 보이는 원문: 제목, 문단(머리는 굵게), 읽은 쪽의 덧붙임 */
  source: { title: string; paragraphs: { heading: string | null; text: string }[]; note: string | null }
}

/** 읽어 오기 결과: 읽음 / 이 그룹에 이미 있는 공고 / 읽지 못함. */
export type ReadPostResult =
  | { status: "ok"; draft: PostDraft }
  | { status: "duplicate"; post: Pick<GroupPost, "id" | "company" | "title" | "postedBy" | "postedAt"> }
  | { status: "failed" }

/** 그룹에 올리는 값(POST /api/groups/{groupId}/posts. 키워드와 묶음은 화면 API 대응 3.2의 8번). */
export interface PostUpload {
  company: string
  title: string
  deadline: Deadline
  sourceUrl: string | null
  applyAt: string | null
  sections: PostSection[]
  keywords: string[]
}

/** 올리기 결과. 같은 공고가 이 그룹에 있으면 서버가 409 ALREADY_IN_GROUP과 그 공고를 알려 준다. */
export type UploadPostResult = { ok: true; postId: string } | { ok: false; error: "ALREADY_IN_GROUP"; postId: string }
