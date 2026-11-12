// 그룹 공고 화면이 데이터를 가져오고 바꾸는 유일한 곳.
// 지금은 백엔드 연결 전이라 예시 데이터를 메모리에 두고 읽고 고친다. 서버가 생기면 이 파일의 함수 안만
// fetch 호출로 바꾸고, 함수 이름과 돌려주는 모양(types.ts)은 그대로 둔다.
import { isClosed } from "./deadline"
import { createExampleGroups, EXAMPLE_TODAY, VIEWER, type ExampleGroup } from "./example-data"
import type {
  Comment,
  CommentPart,
  Group,
  GroupPost,
  GroupPostData,
  GroupPostDetail,
  GroupPostsData,
  MyGroups,
  MyState,
} from "./types"

/** 그룹이나 공고가 없을 때. 라우터의 오류 화면이 "찾을 수 없음"으로 보인다. */
export class NotFoundError extends Error {
  constructor(what: "그룹" | "공고") {
    super(what === "그룹" ? "그룹을 찾을 수 없습니다." : "공고를 찾을 수 없습니다.")
    this.name = "NotFoundError"
  }
}

let store: ExampleGroup[] | null = null
const db = () => (store ??= createExampleGroups())

/** 서버 기준 오늘. 예시 데이터는 디자인과 같은 날(2026-11-13)로 고정한다. */
const today = () => EXAMPLE_TODAY

const copy = <T>(value: T): T => structuredClone(value)

function findGroup(groupId: string): ExampleGroup {
  const group = db().find((g) => g.id === groupId)
  if (!group) throw new NotFoundError("그룹")
  return group
}

function findPost(groupId: string, postId: string): GroupPostDetail {
  const post = findGroup(groupId).posts.find((p) => p.id === postId)
  if (!post) throw new NotFoundError("공고")
  return post
}

function toGroup(g: ExampleGroup): Group {
  return {
    id: g.id,
    name: g.name,
    mark: g.mark,
    members: copy(g.members),
    leaderId: g.leaderId,
    myDisclosure: g.myDisclosure,
    openPostCount: g.posts.filter((p) => !isClosed(p.deadline, today())).length,
  }
}

function toSummary(p: GroupPostDetail): GroupPost {
  const { id, groupId, company, title, deadline, postedBy, postedAt, interests, me, commentCount } = p
  return copy({ id, groupId, company, title, deadline, postedBy, postedAt, interests, me, commentCount })
}

/** 내가 속한 그룹들. 헤더의 그룹 전환과 첫 화면에서 쓴다. */
export async function getMyGroups(): Promise<MyGroups> {
  return { viewer: copy(VIEWER), groups: db().map(toGroup) }
}

/** 그룹 공고 목록. */
export async function getGroupPosts(groupId: string): Promise<GroupPostsData> {
  const group = findGroup(groupId)
  return { today: today(), viewer: copy(VIEWER), group: toGroup(group), posts: group.posts.map(toSummary) }
}

/** 그룹 공고 상세. */
export async function getGroupPost(groupId: string, postId: string): Promise<GroupPostData> {
  const group = findGroup(groupId)
  return { today: today(), viewer: copy(VIEWER), group: toGroup(group), post: copy(findPost(groupId, postId)) }
}

/** 이 그룹에서 이 공고의 관심을 켜고 끈다. */
export async function setInterest(groupId: string, postId: string, interested: boolean): Promise<MyState> {
  const post = findPost(groupId, postId)
  post.me.interested = interested
  return copy(post.me)
}

const URL_PATTERN = /(https?:\/\/[^\s]+)/g

/** 댓글 본문을 글자와 링크로 나눈다. 댓글은 글자와 링크만 받는다. */
function toParts(text: string): CommentPart[] {
  return text
    .split(URL_PATTERN)
    .filter((piece) => piece !== "")
    .map((piece) => (/^https?:\/\//.test(piece) ? { text: piece, href: piece } : { text: piece }))
}

/** 이 그룹의 이 공고에 내 댓글을 단다. */
export async function addComment(groupId: string, postId: string, text: string): Promise<Comment> {
  const post = findPost(groupId, postId)
  const comment: Comment = {
    id: `${postId}-c${Date.now()}`,
    author: copy(VIEWER),
    createdAt: new Date().toISOString(),
    body: toParts(text.trim()),
  }
  post.comments.push(comment)
  post.commentCount += 1
  return copy(comment)
}
