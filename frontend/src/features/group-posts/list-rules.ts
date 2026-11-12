// 그룹 공고 목록의 필터, 정렬, 묶음 규칙. 디자인 파일(docs/design/group-posts.dc.html)의
// renderVals()와 같은 규칙을 화면과 떨어진 순수 함수로 옮겼다. "새 공고"는 리서치 문서의 규칙(올린 지 3일 이내)을 따른다.
import { daysBetween, daysLeft, isClosed, isNewPost } from "./deadline"
import type { GroupPost, Member } from "./types"

export type Tab = "open" | "closed"
export type Sort = "due" | "recent" | "fans"

/** member: "" 전체, "me" 나, 그 밖에는 멤버 id */
export interface ListFilters {
  tab: Tab
  sort: Sort
  member: string
  mine: boolean
  query: string
}

export const DEFAULT_FILTERS: ListFilters = { tab: "open", sort: "due", member: "", mine: false, query: "" }

export const SORT_LABELS: Record<Sort, string> = {
  due: "마감 임박순",
  recent: "최근 올린 순",
  fans: "관심 많은 순",
}

/** 관심 멤버 칸의 칩. 나는 맨 앞이다. */
export type FanChip = { kind: "me" } | { kind: "member"; member: Member; applied: boolean }

export interface PostRow {
  post: GroupPost
  closed: boolean
  kind: "date" | "always" | "unknown"
  isNew: boolean
  /** 내가 관심을 켰거나 내 지원 상태가 있으면 true */
  meOn: boolean
  fans: FanChip[]
}

export interface PostListSection {
  /** 빈 글자면 묶음 이름 없이 줄만 보인다 */
  title: string
  rows: PostRow[]
}

export interface PostList {
  /** 진행 중 / 마감됨 탭의 개수. 다른 필터와 상관없이 센다 */
  counts: Record<Tab, number>
  sections: PostListSection[]
}

export function toRow(post: GroupPost, today: string): PostRow {
  const meOn = post.me.interested || post.me.status !== null
  const fans: FanChip[] = post.interests.map((i) => ({ kind: "member", member: i.member, applied: i.applied }))
  if (meOn) fans.unshift({ kind: "me" })
  return {
    post,
    closed: isClosed(post.deadline, today),
    kind: post.deadline.kind,
    isNew: isNewPost(post.postedAt, post.deadline, today),
    meOn,
    fans,
  }
}

export function matchesFilters(row: PostRow, filters: ListFilters): boolean {
  if (filters.mine && !row.meOn) return false
  if (filters.member === "me" && !row.meOn) return false
  if (filters.member && filters.member !== "me" && !row.post.interests.some((i) => i.member.id === filters.member)) {
    return false
  }
  const query = filters.query.trim().toLowerCase()
  if (query && !`${row.post.company} ${row.post.title}`.toLowerCase().includes(query)) return false
  return true
}

/** 최근 올린 순(늦게 올린 것이 앞). daysBetween(a, b)는 b가 늦을수록 커서 b가 앞으로 온다 */
const byRecent = (a: PostRow, b: PostRow) => daysBetween(a.post.postedAt, b.post.postedAt)

function deadlineDate(row: PostRow): string {
  return row.post.deadline.kind === "date" ? row.post.deadline.date : ""
}

export function buildPostList(posts: GroupPost[], filters: ListFilters, today: string): PostList {
  const rows = posts.map((p) => toRow(p, today))
  const open = rows.filter((r) => !r.closed)
  const closed = rows.filter((r) => r.closed)
  const pass = (r: PostRow) => matchesFilters(r, filters)

  let sections: PostListSection[]
  if (filters.tab === "closed") {
    // 마감됨: 최근 마감순
    sections = [{ title: "", rows: closed.filter(pass).sort((a, b) => daysBetween(deadlineDate(a), deadlineDate(b))) }]
  } else if (filters.sort === "due") {
    const list = open.filter(pass)
    const left = (r: PostRow) => daysLeft(r.post.deadline, today) ?? 0
    sections = [
      { title: "마감일 확인 필요", rows: list.filter((r) => r.kind === "unknown") },
      { title: "", rows: list.filter((r) => r.kind === "date").sort((a, b) => left(a) - left(b)) },
      { title: "상시 채용", rows: list.filter((r) => r.kind === "always").sort(byRecent) },
    ]
  } else {
    const order = filters.sort === "recent" ? byRecent : (a: PostRow, b: PostRow) => b.fans.length - a.fans.length
    sections = [{ title: "", rows: open.filter(pass).sort(order) }]
  }

  return {
    counts: { open: open.length, closed: closed.length },
    sections: sections.filter((s) => s.rows.length > 0),
  }
}
