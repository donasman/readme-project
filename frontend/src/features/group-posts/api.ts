// 그룹 공고 화면이 데이터를 가져오고 바꾸는 유일한 곳.
// 지금은 백엔드 연결 전이라 가짜 서버(mocks/db.ts)를 부른다. 서버가 생기면 이 파일의 함수 안만
// fetch 호출로 바꾸고, 함수 이름과 돌려주는 모양(types.ts)은 그대로 둔다.
import * as db from "@/mocks/db"

import type { Comment, GroupPostData, GroupPostsData, MyGroups, MyState } from "./types"

/** 내가 속한 그룹들. 헤더의 그룹 전환과 첫 화면에서 쓴다. */
export async function getMyGroups(): Promise<MyGroups> {
  return { viewer: db.viewer(), groups: db.listGroups() }
}

/** 그룹 공고 목록. */
export async function getGroupPosts(groupId: string): Promise<GroupPostsData> {
  return { today: db.today(), viewer: db.viewer(), group: db.findGroup(groupId), posts: db.listPosts(groupId) }
}

/** 그룹 공고 상세. */
export async function getGroupPost(groupId: string, postId: string): Promise<GroupPostData> {
  return { today: db.today(), viewer: db.viewer(), group: db.findGroup(groupId), post: db.findPost(groupId, postId) }
}

/** 이 그룹에서 이 공고의 관심을 켜고 끈다. */
export async function setInterest(groupId: string, postId: string, interested: boolean): Promise<MyState> {
  return db.setInterest(groupId, postId, interested)
}

/** 이 그룹의 이 공고에 내 댓글을 단다. */
export async function addComment(groupId: string, postId: string, text: string): Promise<Comment> {
  return db.addComment(groupId, postId, text)
}
