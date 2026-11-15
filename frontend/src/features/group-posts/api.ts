// 그룹 공고 화면이 공고 데이터를 가져오고 바꾸는 유일한 곳. 주소와 오류 이름은 공고 문서 4.1, 4.3이다. 그룹과 보는 사람은 domain에서 읽는다.
import * as db from "@/mocks/db"
import { apiPath, isApiError, mockServer, request } from "@/shared/lib/api"

import type { Comment, Deadline, GroupPostData, GroupPostsData, MyState, PostReadInput, PostUpload, ReadPostResult, UploadPostResult } from "./types"

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

/** 공고를 읽어 칸을 채운다: 링크, 붙여넣은 본문, 파일. 이 그룹에 이미 있으면 그 공고를, 읽지 못하면 failed를 돌려준다. */
export async function readPost(groupId: string, input: PostReadInput): Promise<ReadPostResult> {
  // shortcut: 읽어 오기는 아직 가짜 서버를 부르고 파일은 이름만 보낸다, 공고 올리기를 붙이는 브랜치에서 POST /api/postings/read로 바꾼다(공고 문서 4.6, 8절 13~20번)
  return db.readPosting(groupId, input.kind === "file" ? { kind: "file", name: input.file.name } : input)
}

/** 칸을 확인한 공고를 그룹에 올린다. 같은 공고가 이 그룹에 있으면 409 ALREADY_IN_GROUP과 그 공고의 id. */
export async function uploadPost(groupId: string, input: PostUpload): Promise<UploadPostResult> {
  if (mockServer) return db.uploadPost(groupId, input)
  // shortcut: 서버의 올리기는 묶음(sections)과 키워드를 받지 않아 보내지 않는다, 서버가 받기로 하면 함께 보낸다(화면 API 대응 3.2의 8번)
  const { company, title, deadline, sourceUrl, applyAt } = input
  try {
    const { post } = await request<GroupPostData>("POST", apiPath`/api/groups/${groupId}/posts`, { company, title, deadline, sourceUrl, applyAt })
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
