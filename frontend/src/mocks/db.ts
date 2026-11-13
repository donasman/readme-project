// 가짜 서버. 백엔드를 연결하기 전까지 각 층의 api.ts가 이 함수들을 부른다.
// 예시 데이터를 처음 한 번 만들어 메모리에 두고 읽고 고친다. 새로 고치면 처음 상태로 돌아간다.
// 서버가 할 일(열린 공고 수 세기, 댓글 링크 나누기)도 여기서 흉내 낸다. 백엔드를 연결하면 이 폴더를 지운다.
// 이 폴더는 shared와 타입(import type)만 불러온다.
import type { Group } from "@/domain/group/types"
import type { Viewer } from "@/domain/session/types"
import type { CardTag, ExperienceCard } from "@/features/cards/types"
import type { Comment, CommentPart, GroupPost, GroupPostDetail, MyState } from "@/features/group-posts/types"
import { daysBetween } from "@/shared/lib/date"
import { NotFoundError } from "@/shared/lib/errors"

import { createExampleCards } from "./example-cards"
import { createExampleGroups, EXAMPLE_TODAY, VIEWER, type ExampleGroup } from "./example-data"

let store: ExampleGroup[] | null = null
const data = () => (store ??= createExampleGroups())

/** 돌려주는 값은 늘 복사본이다. 화면이 받은 값을 고쳐도 저장소는 바뀌지 않는다. */
const copy = <T>(value: T): T => structuredClone(value)

function rawGroup(groupId: string): ExampleGroup {
  const group = data().find((g) => g.id === groupId)
  if (!group) throw new NotFoundError("그룹")
  return group
}

function rawPost(groupId: string, postId: string): GroupPostDetail {
  const post = rawGroup(groupId).posts.find((p) => p.id === postId)
  if (!post) throw new NotFoundError("공고")
  return post
}

/** 서버 기준 오늘. 예시 데이터는 디자인과 같은 날(2026-11-13)로 고정한다. */
export const today = (): string => EXAMPLE_TODAY

/** 로그인한 사람. */
export const viewer = (): Viewer => copy(VIEWER)

/**
 * 그룹 전환 메뉴의 "공고 N": 마감되지 않은 공고 수. 서버가 세는 값을 흉내 낸다.
 * 규칙은 features/group-posts/deadline.ts의 isClosed와 같다(마감일 당일은 진행 중).
 */
function openPostCount(group: ExampleGroup): number {
  return group.posts.filter((p) => p.deadline.kind !== "date" || daysBetween(today(), p.deadline.date) >= 0).length
}

function toGroup(g: ExampleGroup): Group {
  return {
    id: g.id,
    name: g.name,
    mark: g.mark,
    members: copy(g.members),
    leaderId: g.leaderId,
    myDisclosure: g.myDisclosure,
    openPostCount: openPostCount(g),
  }
}

function toSummary(p: GroupPostDetail): GroupPost {
  const { id, groupId, company, title, deadline, postedBy, postedAt, interests, me, commentCount } = p
  return copy({ id, groupId, company, title, deadline, postedBy, postedAt, interests, me, commentCount })
}

/** 내가 속한 그룹들. */
export const listGroups = (): Group[] => data().map(toGroup)

/** 그룹 하나. 없으면 NotFoundError. */
export const findGroup = (groupId: string): Group => toGroup(rawGroup(groupId))

/** 그룹의 공고 목록(목록 한 줄 모양). 그룹이 없으면 NotFoundError. */
export const listPosts = (groupId: string): GroupPost[] => rawGroup(groupId).posts.map(toSummary)

/** 그룹 공고 상세. 그룹이나 공고가 없으면 NotFoundError. */
export const findPost = (groupId: string, postId: string): GroupPostDetail => copy(rawPost(groupId, postId))

/** 이 그룹에서 이 공고의 관심을 켜고 끈다. */
export function setInterest(groupId: string, postId: string, interested: boolean): MyState {
  const post = rawPost(groupId, postId)
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
export function addComment(groupId: string, postId: string, text: string): Comment {
  const post = rawPost(groupId, postId)
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

// ---- 경험카드(보는 사람의 카드) ----

let cardStore: ExperienceCard[] | null = null
const cardData = () => (cardStore ??= createExampleCards())

/** 내 경험카드 목록. 순서는 저장된 순서 그대로다. */
export const listCards = (): ExperienceCard[] => copy(cardData())

/** 내 카드에 쓰인 태그와 카드 수(GET /api/cards/tags). 처음 나온 순서로 돌려준다. */
export function listCardTags(): CardTag[] {
  const counts = new Map<string, number>()
  for (const card of cardData()) for (const tag of card.tags) counts.set(tag, (counts.get(tag) ?? 0) + 1)
  return [...counts].map(([name, count]) => ({ name, count }))
}
