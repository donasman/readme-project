// 가짜 서버. 백엔드를 연결하기 전까지 각 층의 api.ts가 이 함수들을 부른다.
// 예시 데이터를 처음 한 번 만들어 메모리에 두고 읽고 고친다. 새로 고치면 처음 상태로 돌아간다.
// 서버가 할 일(열린 공고 수 세기, 댓글 링크 나누기)도 여기서 흉내 낸다.
// shortcut: 이 폴더 전체가 가짜 서버다, 백엔드를 연결하면 각 api.ts를 fetch로 바꾸고 이 폴더를 지운다
// 이 폴더는 shared와 타입(import type)만 불러온다.
import type { Disclosure, Group } from "@/domain/group/types"
import type { Viewer } from "@/domain/session/types"
import type { AcceptInviteResult, InviteView } from "@/features/auth/types"
import type {
  Comment,
  CommentPart,
  Deadline,
  DraftField,
  DraftSection,
  GroupPost,
  GroupPostDetail,
  MyState,
  PostDraft,
  PostUpload,
  ReadPostResult,
  UploadPostResult,
} from "@/features/group-posts/types"
import { addDays, daysBetween } from "@/shared/lib/date"
import { NotFoundError } from "@/shared/lib/errors"

import { KEYWORDS } from "./example-cards"
import { createExampleDraft, createExampleGroups, EXAMPLE_POST_URLS, EXAMPLE_TODAY, VIEWER, type ExampleGroup } from "./example-data"

/**
 * 가짜 서버만의 보기 설정. 처음 여는 주소에 붙인다(새로 고치면 처음 상태로 돌아간다).
 * ?mock=guest: 로그인하지 않은 사람. ?mock=no-groups: 그룹이 하나도 없는 사람(내 그룹의 빈 상태, 초대 수락).
 * ?mock=no-google: 구글을 연결하지 않은 사람(내 정보).
 */
const MOCK = new Set(typeof location === "undefined" ? [] : new URLSearchParams(location.search).getAll("mock"))

let store: ExampleGroup[] | null = null
const data = () => (store ??= startGroups())

/** 그룹 없는 사람이면 보는 사람을 모든 그룹에서 빼고, 보는 사람이 그룹장인 그룹은 없앤다. */
function startGroups(): ExampleGroup[] {
  const groups = createExampleGroups()
  if (!MOCK.has("no-groups")) return groups
  return groups
    .filter((g) => g.leaderId !== VIEWER.id)
    .map((g) => ({ ...g, members: g.members.filter((m) => m.id !== VIEWER.id) }))
}

const isMember = (g: ExampleGroup) => g.members.some((m) => m.id === VIEWER.id)

/** 돌려주는 값은 늘 복사본이다. 화면이 받은 값을 고쳐도 저장소는 바뀌지 않는다. */
const copy = <T>(value: T): T => structuredClone(value)

function rawGroup(groupId: string): ExampleGroup {
  const group = data().find((g) => g.id === groupId && isMember(g))
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

/** 로그인한 사람. 로그인하지 않았으면 null(서버의 401). */
export const viewer = (): Viewer | null => (MOCK.has("guest") ? null : copy(VIEWER))

/** 가짜 서버의 보기 설정을 읽는다(같은 폴더의 다른 가짜 서버가 쓴다). */
export const mockFlag = (name: string) => MOCK.has(name)

/** 로그아웃(POST /api/auth/logout)과 탈퇴 뒤: 이 뒤로는 로그인하지 않은 사람으로 본다. */
export function signOut(): void {
  MOCK.add("guest")
}

/** 표시 이름 바꾸기. 그룹 멤버에게 보이는 이름이다. */
export function renameViewer(name: string): Viewer {
  VIEWER.name = name
  return copy(VIEWER)
}

/**
 * 그룹 전환 메뉴의 "공고 N": 마감되지 않은 공고 수. 서버가 세는 값을 흉내 낸다.
 * 규칙은 features/group-posts/deadline.ts의 isClosed와 같다(마감일 당일은 진행 중).
 */
function openPostCount(group: ExampleGroup): number {
  return group.posts.filter((p) => p.deadline.kind !== "date" || daysBetween(today(), p.deadline.date) >= 0).length
}

/** 진행 중인 공고 가운데 마감일이 가장 가까운 것. 서버가 고르는 값을 흉내 낸다. */
function nearestDeadline(group: ExampleGroup): Group["nearestDeadline"] {
  const next = group.posts
    .flatMap((p) => (p.deadline.kind === "date" && daysBetween(today(), p.deadline.date) >= 0 ? [{ date: p.deadline.date, company: p.company, title: p.title }] : []))
    .sort((a, b) => a.date.localeCompare(b.date))[0]
  return next ?? null
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
    nearestDeadline: nearestDeadline(g),
  }
}

function toSummary(p: GroupPostDetail): GroupPost {
  const { id, groupId, company, title, deadline, postedBy, postedAt, interests, me, commentCount } = p
  return copy({ id, groupId, company, title, deadline, postedBy, postedAt, interests, me, commentCount })
}

/** 내가 속한 그룹들. */
export const listGroups = (): Group[] => data().filter(isMember).map(toGroup)

/** 그룹 하나. 없으면 NotFoundError. */
export const findGroup = (groupId: string): Group => toGroup(rawGroup(groupId))

/** 그룹의 공고 목록(목록 한 줄 모양). 그룹이 없으면 NotFoundError. */
export const listPosts = (groupId: string): GroupPost[] => rawGroup(groupId).posts.map(toSummary)

/** 그룹 공고 상세. 그룹이나 공고가 없으면 NotFoundError. */
export const findPost = (groupId: string, postId: string): GroupPostDetail => copy(rawPost(groupId, postId))

let nextGroupId = 1

/** 그룹 만들기(POST /api/groups). 만든 사람이 그룹장이고 첫 멤버다. */
export function createGroup(name: string, mark: string): Group {
  const group: ExampleGroup = {
    id: `new${nextGroupId++}`,
    name,
    mark,
    members: [{ ...VIEWER, joinedAt: today() }],
    leaderId: VIEWER.id,
    myDisclosure: "applied",
    posts: [],
  }
  data().push(group)
  return toGroup(group)
}

/** 이 그룹에 내 공개 범위 정하기(PUT /api/groups/{groupId}/me/disclosure). */
export function setMyDisclosure(groupId: string, disclosure: Disclosure): void {
  rawGroup(groupId).myDisclosure = disclosure
}

/** 그룹 나가기(DELETE /api/groups/{groupId}/me). 그룹장은 나갈 수 없다. 이 그룹에서 켠 관심은 지워진다. */
export function leaveGroup(groupId: string): void {
  const group = rawGroup(groupId)
  if (group.leaderId === VIEWER.id) throw new Error("그룹장은 나갈 수 없습니다.")
  group.members = group.members.filter((m) => m.id !== VIEWER.id)
  for (const post of group.posts) post.me.interested = false
}

// ---- 초대 링크. 링크는 7일 동안 쓸 수 있고, 멤버 누구나 만든다(그룹 설계 가정 6) ----

interface Invite {
  groupId: string
  inviter: Viewer
  expiresAt: string
}
/** 예시 초대: 가람이 만든 백엔드 스터디 링크(시안 7차 "초대 수락"). "expired"는 지난 링크로 친다. */
const invites = new Map<string, Invite>([["Xk3f9QmT2a", { groupId: "be", inviter: { id: "garam", name: "가람" }, expiresAt: "2026-11-20" }]])

/** 초대 링크 만들기(POST /api/groups/{groupId}/invites). 링크는 만들 때만 보여 준다. */
export function createInvite(groupId: string): { token: string; expiresAt: string } {
  rawGroup(groupId)
  const token = Math.random().toString(36).slice(2, 12)
  const expiresAt = addDays(today(), 7)
  invites.set(token, { groupId, inviter: copy(VIEWER), expiresAt })
  return { token, expiresAt }
}

function openInvite(token: string): { invite: Invite; group: ExampleGroup } | null {
  const invite = invites.get(token)
  const group = invite && data().find((g) => g.id === invite.groupId)
  if (!invite || !group || daysBetween(today(), invite.expiresAt) < 0) return null
  return { invite, group }
}

/** 초대 내용(화면 API 대응 3.1의 1번). 없거나 지난 링크는 expired. */
export function getInvite(token: string): InviteView {
  const found = openInvite(token)
  if (!found) return { status: "expired" }
  const { invite, group } = found
  return { status: "open", groupId: group.id, groupName: group.name, mark: group.mark, inviterName: invite.inviter.name, memberCount: group.members.length }
}

/** 초대 수락(POST /api/group-invites/{token}/accept). 이미 멤버면 그대로 둔다. 지난 링크는 410 INVITE_EXPIRED. */
export function acceptInvite(token: string): AcceptInviteResult {
  const found = openInvite(token)
  if (!found) return { ok: false, error: "INVITE_EXPIRED" }
  const { group } = found
  if (!isMember(group)) {
    group.members.push({ ...VIEWER, joinedAt: today() })
    group.myDisclosure = "applied"
  }
  return { ok: true, groupId: group.id }
}

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

/** 댓글 고치기(PUT …/comments/{commentId}). 쓴 사람만 고친다. */
export function updateComment(groupId: string, postId: string, commentId: string, text: string): Comment {
  const comment = rawPost(groupId, postId).comments.find((c) => c.id === commentId)
  if (!comment) throw new NotFoundError("댓글")
  if (comment.author.id !== VIEWER.id) throw new Error("쓴 사람만 고칠 수 있습니다.")
  comment.body = toParts(text.trim())
  return copy(comment)
}

/** 댓글 지우기(DELETE …/comments/{commentId}). 쓴 사람과 그룹장이 지운다. 되돌릴 수 없다. */
export function deleteComment(groupId: string, postId: string, commentId: string): void {
  const group = rawGroup(groupId)
  const post = rawPost(groupId, postId)
  const comment = post.comments.find((c) => c.id === commentId)
  if (!comment) throw new NotFoundError("댓글")
  if (comment.author.id !== VIEWER.id && group.leaderId !== VIEWER.id) throw new Error("쓴 사람과 그룹장만 지울 수 있습니다.")
  post.comments = post.comments.filter((c) => c.id !== commentId)
  post.commentCount -= 1
}

/**
 * 날짜 넣기(PUT …/posts/{postId}/deadline). 마감을 못 읽은 공고에만, 한 번. 날짜나 상시를 받는다(그룹 설계 4.2 아래 "정함").
 * 공고 본체는 그룹들이 함께 쓰므로 이 공고가 올라간 다른 그룹에도 보인다(가짜 서버는 그룹마다 따로 둔다).
 */
export function setDeadline(groupId: string, postId: string, deadline: Deadline): { ok: true; deadline: Deadline } | { ok: false; error: "DEADLINE_ALREADY_SET" } {
  const post = rawPost(groupId, postId)
  if (post.deadline.kind !== "unknown") return { ok: false, error: "DEADLINE_ALREADY_SET" }
  post.deadline = copy(deadline)
  return { ok: true, deadline: copy(post.deadline) }
}

/** 그룹에서 내린 공고. 같은 공고를 다시 올리면 댓글과 관심 그대로 돌아온다(그룹 설계 가정 4). */
const takenDown = new Map<string, GroupPostDetail[]>()

/** 그룹에서 내리기(DELETE …/posts/{postId}). 올린 사람과 그룹장만 한다. */
export function takeDownPost(groupId: string, postId: string): void {
  const group = rawGroup(groupId)
  const post = rawPost(groupId, postId)
  if (post.postedBy.id !== VIEWER.id && group.leaderId !== VIEWER.id) throw new Error("올린 사람과 그룹장만 내릴 수 있습니다.")
  group.posts = group.posts.filter((p) => p.id !== postId)
  takenDown.set(groupId, [...(takenDown.get(groupId) ?? []), post])
}

// ---- 공고 읽어 오기와 올리기. 읽어 오기는 아직 설계에 없다(화면 API 대응 3.2의 7~9번). 여기서는 흉내만 낸다 ----

const postUrls = new Map(Object.entries(EXAMPLE_POST_URLS))

/** 이 그룹에 이미 있는 공고. 원문 주소나 회사·공고명이 같으면 같은 공고로 본다. */
function findSamePost(group: ExampleGroup, by: { url?: string; company?: string; title?: string }) {
  const id = by.url ? postUrls.get(by.url.trim()) : undefined
  return group.posts.find((p) => p.id === id || (by.company !== undefined && p.company === by.company && p.title === by.title))
}

const SECTION_HEADINGS: Record<string, string> = { "주요 업무": "주요 업무", "담당 업무": "주요 업무", "자격 요건": "필수 요건", "필수 요건": "필수 요건", "우대 사항": "우대 사항" }

/** 붙여넣은 본문을 칸으로 나눈다: 첫 줄의 [회사] 공고명, 날짜나 상시, 묶음 머리 아래 줄들, 사전에 있는 키워드. */
function parsePostText(text: string, sourceUrl: string): PostDraft {
  const lines = text.split("\n").map((l) => l.trim()).filter(Boolean)
  const first = lines[0] ?? ""
  const head = first.match(/^\[(.+?)\]\s*(.+?)(?:\s*채용)?$/)
  const date = text.match(/(\d{4})[-.](\d{1,2})[-.](\d{1,2})/)
  const deadline: Deadline = date
    ? { kind: "date", date: `${date[1]}-${date[2].padStart(2, "0")}-${date[3].padStart(2, "0")}` }
    : /상시|수시/.test(text)
      ? { kind: "always" }
      : { kind: "unknown" }
  const sections: DraftSection[] = []
  const paragraphs: PostDraft["source"]["paragraphs"] = []
  for (const line of lines.slice(1)) {
    const title = SECTION_HEADINGS[line]
    if (title) {
      sections.push({ title, items: [], from: line })
      paragraphs.push({ heading: line, text: "" })
    } else {
      sections.at(-1)?.items.push(line)
      const last = paragraphs.at(-1)
      if (last?.heading && !last.text) last.text = line
      else paragraphs.push({ heading: null, text: line })
    }
  }
  const unread: DraftField[] = []
  if (!head) unread.push("company", "title")
  if (deadline.kind === "unknown") unread.push("deadline")
  if (!sourceUrl.trim()) unread.push("sourceUrl")
  return {
    company: head?.[1] ?? "",
    title: head?.[2] ?? "",
    deadline,
    sourceUrl: sourceUrl.trim(),
    applyAt: "",
    sections,
    keywords: KEYWORDS.filter((k) => text.toLowerCase().includes(k.toLowerCase())),
    evidence: head ? { company: `첫 줄 "${first}"`, title: "첫 줄" } : {},
    unread,
    source: { title: first, paragraphs, note: null },
  }
}

/** 공고 읽어 오기. 가짜 서버: 주소에 login이나 fail이 들어가면 읽지 못한 것으로, 그 밖의 링크와 파일은 예시 공고로 읽는다. */
export function readPosting(groupId: string, input: { kind: "url"; url: string } | { kind: "text"; text: string; sourceUrl: string } | { kind: "file"; name: string }): ReadPostResult {
  const group = rawGroup(groupId)
  const draft =
    input.kind === "url"
      ? /login|fail/i.test(input.url)
        ? null
        : createExampleDraft(input.url.trim())
      : input.kind === "text"
        ? parsePostText(input.text, input.sourceUrl)
        : { ...createExampleDraft(""), unread: ["deadline", "sourceUrl"] as DraftField[] }
  if (!draft) return { status: "failed" }
  const same = findSamePost(group, { url: input.kind === "url" ? input.url : draft.sourceUrl })
  if (same) {
    const { id, company, title, postedBy, postedAt } = same
    return { status: "duplicate", post: copy({ id, company, title, postedBy, postedAt }) }
  }
  return { status: "ok", draft }
}

let nextPostId = 1

/** 그룹에 공고 올리기(POST /api/groups/{groupId}/posts). 같은 공고가 있으면 409 ALREADY_IN_GROUP. */
export function uploadPost(groupId: string, input: PostUpload): UploadPostResult {
  const group = rawGroup(groupId)
  const same = findSamePost(group, { url: input.sourceUrl ?? undefined, company: input.company, title: input.title })
  if (same) return { ok: false, error: "ALREADY_IN_GROUP", postId: same.id }
  // 내렸던 공고면 댓글과 관심 그대로 돌아온다
  const gone = takenDown.get(groupId) ?? []
  const back = gone.find((p) => p.company === input.company && p.title === input.title)
  if (back) {
    takenDown.set(groupId, gone.filter((p) => p !== back))
    group.posts.unshift(back)
    return { ok: true, postId: back.id }
  }
  const post: GroupPostDetail = {
    id: `${groupId}-new${nextPostId++}`,
    groupId,
    company: input.company,
    title: input.title,
    deadline: input.deadline,
    postedBy: copy(VIEWER),
    postedAt: today(),
    interests: [],
    me: { interested: false, status: null },
    commentCount: 0,
    deadlineName: null,
    applyAt: input.applyAt,
    sourceUrl: input.sourceUrl,
    sections: copy(input.sections),
    keywords: [...input.keywords],
    comments: [],
  }
  group.posts.unshift(post)
  if (input.sourceUrl) postUrls.set(input.sourceUrl, post.id)
  return { ok: true, postId: post.id }
}

// ---- 계정(가입, 인증 메일, 비밀번호 재설정). 서버는 메일을 보내고, 화면은 결과만 받는다 ----

/** 이미 가입된 계정. 회원가입에서 "이미 가입된 이메일"을 볼 때 쓴다. */
const accounts = new Set(["daon@example.com"])

/** 메일 링크의 토큰. 예시에서는 "expired"면 만료된 링크, 빈 값도 쓸 수 없는 링크, 그 밖에는 쓸 수 있는 링크로 친다. */
const usableToken = (token: string | null) => !!token && token !== "expired"

/** 가입(POST /api/auth/signup). 이미 있는 이메일이면 409처럼 EMAIL_TAKEN. */
export function signup(email: string): { ok: true } | { ok: false; error: "EMAIL_TAKEN" } {
  const key = email.trim().toLowerCase()
  if (accounts.has(key)) return { ok: false, error: "EMAIL_TAKEN" }
  accounts.add(key)
  return { ok: true }
}

/** 가입 확인 메일 다시 보내기. 계정이 있는지 알려 주지 않는다. */
export function resendVerification(email: string): void {
  void email
}

/** 가입 확인 링크(GET /api/auth/verify?token=). */
export function verifyEmail(token: string | null): "done" | "expired" {
  return usableToken(token) ? "done" : "expired"
}

/** 재설정 메일 보내기(POST /api/auth/password-reset/request). 계정이 있는지 알려 주지 않는다. */
export function requestPasswordReset(email: string): void {
  void email
}

/** 재설정 링크가 아직 쓸 수 있는지. */
export const checkResetToken = (token: string | null): boolean => usableToken(token)

/** 새 비밀번호로 바꾸기(POST /api/auth/password-reset/confirm). 그 계정의 세션은 모두 끊긴다. */
export function resetPassword(token: string | null, password: string): "done" | "expired" {
  void password
  return usableToken(token) ? "done" : "expired"
}

