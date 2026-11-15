import { describe, expect, it } from "vitest"

import type { Member } from "@/domain/group/types"

import { buildPostList, DEFAULT_FILTERS, knownMember, matchesFilters, toRow, type ListFilters } from "./list-rules"
import type { Deadline, GroupPost } from "./types"

const TODAY = "2026-11-13"
const GARAM: Member = { id: "garam", name: "가람" }
const NARAE: Member = { id: "narae", name: "나래" }

let seq = 0
/** 목록 한 줄. 필요한 값만 바꿔 만든다. */
function post(over: Partial<GroupPost> & { due?: string } = {}): GroupPost {
  const { due, ...rest } = over
  const deadline: Deadline =
    due === "always" ? { kind: "always" } : due === "unknown" ? { kind: "unknown" } : { kind: "date", date: due ?? "2026-11-20" }
  seq += 1
  return {
    id: `p${seq}`,
    groupId: "be",
    company: `회사${seq}`,
    title: "백엔드 엔지니어",
    deadline,
    postedBy: GARAM,
    postedAt: "2026-11-01",
    interests: [],
    me: { interested: false, status: null },
    commentCount: 0,
    ...rest,
  }
}

const filters = (over: Partial<ListFilters> = {}): ListFilters => ({ ...DEFAULT_FILTERS, ...over })
const ids = (list: ReturnType<typeof buildPostList>) => list.sections.map((s) => [s.title, s.rows.map((r) => r.post.company)])

describe("toRow", () => {
  it("관심을 켰으면 나를 관심 멤버 맨 앞에 둔다", () => {
    const interests = [{ member: NARAE, applied: true }]
    expect(toRow(post({ interests, me: { interested: true, status: null } }), TODAY).fans).toEqual([
      { kind: "me" },
      { kind: "member", member: NARAE, applied: true },
    ])
    expect(toRow(post({ interests }), TODAY).fans).toEqual([{ kind: "member", member: NARAE, applied: true }])
  })
  it("관심을 끈 공고는 지원 상태가 있어도 나를 넣지 않는다(다른 멤버가 보는 것과 같다, 공고 문서 4.5)", () => {
    const row = toRow(post({ me: { interested: false, status: "필기 단계" } }), TODAY)
    expect(row.meOn).toBe(false)
    expect(row.fans).toEqual([])
  })
})

describe("matchesFilters", () => {
  const mine = toRow(post({ company: "예시 에듀테크", me: { interested: true, status: null } }), TODAY)
  const others = toRow(post({ company: "다른 회사", title: "Data Engineer", interests: [{ member: GARAM, applied: false }] }), TODAY)

  it("내 관심만", () => {
    expect(matchesFilters(mine, filters({ mine: true }))).toBe(true)
    expect(matchesFilters(others, filters({ mine: true }))).toBe(false)
  })
  it("멤버: 나, 다른 멤버", () => {
    expect(matchesFilters(mine, filters({ member: "me" }))).toBe(true)
    expect(matchesFilters(others, filters({ member: "me" }))).toBe(false)
    expect(matchesFilters(others, filters({ member: "garam" }))).toBe(true)
    expect(matchesFilters(mine, filters({ member: "garam" }))).toBe(false)
  })
  it("검색어는 회사명과 공고명에서, 앞뒤 공백과 대소문자 무시", () => {
    expect(matchesFilters(mine, filters({ query: " 에듀 " }))).toBe(true)
    expect(matchesFilters(others, filters({ query: "data" }))).toBe(true)
    expect(matchesFilters(others, filters({ query: "에듀" }))).toBe(false)
  })
})

describe("buildPostList", () => {
  const posts = [
    post({ company: "D-7", due: "2026-11-20" }),
    post({ company: "D-1", due: "2026-11-14" }),
    post({ company: "모름", due: "unknown" }),
    post({ company: "상시-옛날", due: "always", postedAt: "2026-10-01" }),
    post({ company: "상시-최근", due: "always", postedAt: "2026-11-12" }),
    post({ company: "어제 마감", due: "2026-11-12" }),
    post({ company: "지난주 마감", due: "2026-11-06" }),
  ]

  it("탭 개수는 다른 필터와 상관없이 센다", () => {
    expect(buildPostList(posts, filters({ query: "없는 회사" }), TODAY).counts).toEqual({ open: 5, closed: 2 })
  })

  it("마감 임박순: 날짜 모름 → 남은 날 짧은 순 → 상시 채용(최근 올린 순)", () => {
    expect(ids(buildPostList(posts, filters(), TODAY))).toEqual([
      ["마감일 확인 필요", ["모름"]],
      ["", ["D-1", "D-7"]],
      ["상시 채용", ["상시-최근", "상시-옛날"]],
    ])
  })

  it("빈 묶음은 뺀다", () => {
    expect(ids(buildPostList(posts, filters({ query: "상시" }), TODAY))).toEqual([["상시 채용", ["상시-최근", "상시-옛날"]]])
  })

  it("마감됨 탭: 최근 마감한 것이 앞", () => {
    expect(ids(buildPostList(posts, filters({ tab: "closed" }), TODAY))).toEqual([["", ["어제 마감", "지난주 마감"]]])
  })

  it("최근 올린 순: 묶음 없이 늦게 올린 것이 앞", () => {
    const list = [post({ company: "가", postedAt: "2026-11-01" }), post({ company: "나", postedAt: "2026-11-11" })]
    expect(ids(buildPostList(list, filters({ sort: "recent" }), TODAY))).toEqual([["", ["나", "가"]]])
  })

  it("관심 많은 순: 나를 포함한 관심 멤버 수", () => {
    const list = [
      post({ company: "한 명", interests: [{ member: GARAM, applied: false }] }),
      post({ company: "두 명(나 포함)", interests: [{ member: GARAM, applied: false }], me: { interested: true, status: null } }),
      post({ company: "없음" }),
    ]
    expect(ids(buildPostList(list, filters({ sort: "fans" }), TODAY))).toEqual([["", ["두 명(나 포함)", "한 명", "없음"]]])
  })
})

describe("knownMember", () => {
  const members: Member[] = [{ id: "garam", name: "가람" }]
  it("나와 지금 멤버만 거르고, 나간 멤버나 틀린 값은 전체로 본다", () => {
    expect(knownMember("garam", members)).toBe("garam")
    expect(knownMember("me", members)).toBe("me")
    expect(knownMember("xyz", members)).toBe("")
  })
})
