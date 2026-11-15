// 공고 본체의 값 가운데 두 기능 이상이 쓰는 것(그룹 공고, 내 공고). 날짜는 한국 시간 기준 "YYYY-MM-DD".

/** 마감. 날짜를 못 읽었으면 unknown, 상시·수시 채용이면 always. */
export type Deadline =
  | { kind: "date"; date: string }
  | { kind: "always" }
  | { kind: "unknown" }

/** 공고 내용의 한 묶음(주요 업무, 필수 요건 …). */
export interface PostSection {
  title: string
  items: string[]
}

/** 다른 화면이 그룹 공고 목록으로 보내며 "마감일 넣기" 창을 바로 열게 할 때 넘기는 값(홈의 확인 필요). */
export interface FillDeadlineState {
  fillDeadline: string
}

// ---- 공고 읽어 오기와 칸 확인(시안 7차 "공고 올리기", "칸 확인", 10차 "내 공고 넣기") ----
// 그룹에 올리기와 그룹 없이 내 공고에 넣기가 함께 쓴다. 읽어 오기(POST /api/postings/read, 공고 문서 4.6)의 응답을
// api.ts의 readPosting이 아래 모양으로 나눈다.

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
  /** 오른쪽에 보이는 원문: 제목과 줄바꿈이 든 글. 문단은 sections[].from(머리 줄)으로 나눈다(post-source.ts) */
  source: { title: string; text: string }
}

/** 그 그룹에 이미 있는 공고(그룹에 올릴 때만 서버가 알려 준다). 올린 사람이 탈퇴했으면 postedBy가 null이다. */
export interface DuplicatePost {
  id: string
  company: string
  title: string
  postedBy: { id: string; name: string } | null
  postedAt: string
}

/** 읽어 오기 결과: 읽음 / 이 그룹에 이미 있는 공고 / 읽지 못함(서버가 준 이유와 붙여넣기 안내). */
export type ReadPostResult = { status: "ok"; draft: PostDraft } | { status: "duplicate"; post: DuplicatePost } | { status: "failed"; message: string }

/** 올리거나 넣는 값. 그룹에 올리기(POST /api/groups/{groupId}/posts)와 내 공고 넣기(POST /api/me/postings)가 같은 본문이다(공고 문서 4.4). */
export interface PostUpload {
  company: string
  title: string
  deadline: Deadline
  sourceUrl: string | null
  applyAt: string | null
  sections: PostSection[]
  keywords: string[]
}

/** 읽어 오기 화면이 읽은 결과를 들고 칸 확인 화면으로 온다(주소 상태). */
export interface PostCheckState {
  draft: PostDraft
}
