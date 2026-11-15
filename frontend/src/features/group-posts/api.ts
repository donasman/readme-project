// 그룹 공고 화면이 공고 데이터를 가져오고 바꾸는 유일한 곳. 주소와 오류 이름은 공고 문서 4.1, 4.3이다. 그룹과 보는 사람은 domain에서 읽는다.
import * as db from "@/mocks/db"
import { apiPath, isApiError, mockServer, request } from "@/shared/lib/api"
import { sentence } from "@/shared/lib/korean"

import type { Comment, Deadline, DraftField, GroupPost, GroupPostData, GroupPostsData, MyState, PostDraft, PostReadInput, PostUpload, ReadPostResult, UploadPostResult } from "./types"

/** 그룹 공고 목록. 내린 공고는 빠지고 마감된 공고는 들어 있다. */
export async function getGroupPosts(groupId: string): Promise<GroupPostsData> {
  if (mockServer) return { today: db.today(), posts: db.listPosts(groupId) }
  return request("GET", apiPath`/api/groups/${groupId}/posts`)
}

/** 그룹 공고 상세. */
export async function getGroupPost(groupId: string, postId: string): Promise<GroupPostData> {
  if (mockServer) return { today: db.today(), post: db.findPost(groupId, postId) }
  return request("GET", apiPath`/api/groups/${groupId}/posts/${postId}`)
}

/** 이 그룹에서 이 공고의 관심을 켜고 끈다. 켤 때 내 공고에 없으면 서버가 넣는다. */
export async function setInterest(groupId: string, postId: string, interested: boolean): Promise<MyState> {
  if (mockServer) return db.setInterest(groupId, postId, interested)
  return request("PUT", apiPath`/api/groups/${groupId}/posts/${postId}/interest`, { interested })
}

/** 이 그룹의 이 공고에 내 댓글을 단다. 서버가 링크를 조각으로 나눠 돌려준다. */
export async function addComment(groupId: string, postId: string, text: string): Promise<Comment> {
  if (mockServer) return db.addComment(groupId, postId, text)
  return request("POST", apiPath`/api/groups/${groupId}/posts/${postId}/comments`, { text })
}

/** 읽어 오기의 응답(공고 문서 4.6). 못 읽은 칸은 ""이고, 그 그룹에 같은 공고가 있으면 duplicate가 붙는다. 키워드는 사전의 대표 표기다. */
interface ReadResponse extends Omit<PostDraft, "unread"> {
  unread: string[]
  duplicate?: Pick<GroupPost, "company" | "title" | "postedBy" | "postedAt"> & { postId: string }
}

/** 칸 확인 화면이 아는 칸. 서버의 unread에는 화면에 없는 deadlineName도 늘 들어 있다(공고 문서 8절 16번). */
const DRAFT_FIELDS: readonly string[] = ["company", "title", "deadline", "sourceUrl", "applyAt"] satisfies DraftField[]

/** 공고를 읽어 칸을 채운다: 링크, 붙여넣은 본문, 파일. 이 그룹에 이미 있으면 그 공고를, 읽지 못하면 이유를 돌려준다. */
export async function readPost(groupId: string, input: PostReadInput): Promise<ReadPostResult> {
  if (mockServer) return db.readPosting(groupId, input.kind === "file" ? { kind: "file", name: input.file.name } : input)
  // shortcut: 서버에 파일 읽기가 아직 없어 붙여넣기로 보낸다, 서버가 파일을 받으면 보낸다(공고 문서 4.6)
  if (input.kind === "file") return { status: "failed", message: "파일은 아직 읽지 못합니다. 공고 본문을 복사해 붙여넣어 주세요." }
  const from = input.kind === "url" ? { url: input.url } : { text: input.text, sourceUrl: input.sourceUrl }
  try {
    // groupId를 주면 서버가 그 그룹에 같은 공고가 있는지 함께 본다. 서버의 id는 숫자다
    const { duplicate, unread, ...draft } = await request<ReadResponse>("POST", "/api/postings/read", { ...from, groupId: Number(groupId) })
    if (duplicate) {
      const { postId, ...post } = duplicate
      return { status: "duplicate", post: { id: postId, ...post } }
    }
    return { status: "ok", draft: { ...draft, unread: unread.filter((f): f is DraftField => DRAFT_FIELDS.includes(f)) } }
  } catch (error) {
    if (isApiError(error, "FETCH_BLOCKED", "FETCH_FAILED")) return { status: "failed", message: sentence(error.message) }
    throw error
  }
}

/**
 * 칸을 확인한 공고를 그룹에 올린다. 같은 공고가 이 그룹에 있으면 409 ALREADY_IN_GROUP과 그 공고의 id.
 * 서버가 키워드를 대표 표기로 바꾸고 묶음을 다시 검사한다. 묶음이 모두 합쳐 5만 자를 넘는 400은 화면 아래 알림이 서버의 글로 보인다.
 */
export async function uploadPost(groupId: string, input: PostUpload): Promise<UploadPostResult> {
  if (mockServer) return db.uploadPost(groupId, input)
  try {
    const { post } = await request<GroupPostData>("POST", apiPath`/api/groups/${groupId}/posts`, input)
    return { ok: true, postId: post.id }
  } catch (error) {
    if (isApiError(error, "ALREADY_IN_GROUP")) return { ok: false, error: "ALREADY_IN_GROUP", postId: String(error.body.postId) }
    throw error
  }
}

/** 마감을 못 읽은 공고에 날짜나 상시를 넣는다. 한 번 넣으면 바꿀 수 없다(이미 있으면 409 DEADLINE_ALREADY_SET이고 false). */
export async function setDeadline(groupId: string, postId: string, deadline: Deadline): Promise<boolean> {
  if (mockServer) return db.setDeadline(groupId, postId, deadline).ok
  try {
    await request("PUT", apiPath`/api/groups/${groupId}/posts/${postId}/deadline`, deadline)
    return true
  } catch (error) {
    if (isApiError(error, "DEADLINE_ALREADY_SET")) return false
    throw error
  }
}

/** 그룹에서 내린다. 댓글과 관심은 남고, 같은 공고를 다시 올리면 돌아온다. */
export async function takeDownPost(groupId: string, postId: string): Promise<void> {
  if (mockServer) return db.takeDownPost(groupId, postId)
  await request("DELETE", apiPath`/api/groups/${groupId}/posts/${postId}`)
}

/** 내 댓글을 고친다. */
export async function updateComment(groupId: string, postId: string, commentId: string, text: string): Promise<Comment> {
  if (mockServer) return db.updateComment(groupId, postId, commentId, text)
  return request("PUT", apiPath`/api/groups/${groupId}/posts/${postId}/comments/${commentId}`, { text })
}

/** 댓글을 지운다(내 댓글, 그룹장은 남의 댓글도). 되돌릴 수 없다. */
export async function deleteComment(groupId: string, postId: string, commentId: string): Promise<void> {
  if (mockServer) return db.deleteComment(groupId, postId, commentId)
  await request("DELETE", apiPath`/api/groups/${groupId}/posts/${postId}/comments/${commentId}`)
}
