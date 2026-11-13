// 그룹 공고 화면이 공고 데이터를 가져오고 바꾸는 유일한 곳. 그룹과 보는 사람은 domain에서 읽는다.
// 지금은 백엔드 연결 전이라 가짜 서버(mocks/db.ts)를 부른다. 서버가 생기면 이 파일의 함수 안만
// fetch 호출로 바꾸고, 함수 이름과 돌려주는 모양(types.ts)은 그대로 둔다.
import * as db from "@/mocks/db"

import type { Comment, Deadline, GroupPostData, GroupPostsData, MyState, PostReadInput, PostUpload, ReadPostResult, UploadPostResult } from "./types"

/** 그룹 공고 목록. */
export async function getGroupPosts(groupId: string): Promise<GroupPostsData> {
  return { today: db.today(), posts: db.listPosts(groupId) }
}

/** 그룹 공고 상세. */
export async function getGroupPost(groupId: string, postId: string): Promise<GroupPostData> {
  return { today: db.today(), post: db.findPost(groupId, postId) }
}

/** 이 그룹에서 이 공고의 관심을 켜고 끈다. */
export async function setInterest(groupId: string, postId: string, interested: boolean): Promise<MyState> {
  return db.setInterest(groupId, postId, interested)
}

/** 이 그룹의 이 공고에 내 댓글을 단다. */
export async function addComment(groupId: string, postId: string, text: string): Promise<Comment> {
  return db.addComment(groupId, postId, text)
}

/** 공고를 읽어 칸을 채운다: 링크, 붙여넣은 본문, 파일. 이 그룹에 이미 있으면 그 공고를, 읽지 못하면 failed를 돌려준다. */
export async function readPost(groupId: string, input: PostReadInput): Promise<ReadPostResult> {
  // 가짜 서버는 파일을 읽지 않는다. 서버가 생기면 파일을 그대로 보낸다
  return db.readPosting(groupId, input.kind === "file" ? { kind: "file", name: input.file.name } : input)
}

/** 칸을 확인한 공고를 그룹에 올린다. */
export async function uploadPost(groupId: string, input: PostUpload): Promise<UploadPostResult> {
  return db.uploadPost(groupId, input)
}

/** 마감을 못 읽은 공고에 날짜나 상시를 넣는다. 한 번 넣으면 바꿀 수 없다(이미 있으면 409 DEADLINE_ALREADY_SET). */
export async function setDeadline(groupId: string, postId: string, deadline: Deadline): Promise<boolean> {
  return db.setDeadline(groupId, postId, deadline).ok
}

/** 그룹에서 내린다. 댓글과 관심은 남고, 같은 공고를 다시 올리면 돌아온다. */
export async function takeDownPost(groupId: string, postId: string): Promise<void> {
  db.takeDownPost(groupId, postId)
}

/** 내 댓글을 고친다. */
export async function updateComment(groupId: string, postId: string, commentId: string, text: string): Promise<Comment> {
  return db.updateComment(groupId, postId, commentId, text)
}

/** 댓글을 지운다(내 댓글, 그룹장은 남의 댓글도). 되돌릴 수 없다. */
export async function deleteComment(groupId: string, postId: string, commentId: string): Promise<void> {
  db.deleteComment(groupId, postId, commentId)
}
