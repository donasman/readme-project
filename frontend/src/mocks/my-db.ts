// 가짜 서버의 내 공고 쪽: 관심을 켜거나 직접 넣은 공고와 내 지원 상태, 메모, 맞는 카드, 홈의 모음.
// 내 지원 상태와 메모는 내 공고 행(user-postings.ts)에 있고, 그룹 공고와 메일 확인함도 같은 행을 읽고 쓴다.
// 공고 본체(회사, 공고명, 마감, 내용)는 그룹 공고(db.ts)에서 그때그때 읽는다. 그룹에서 마감일을 넣으면 여기도 바뀐다.
// 내 공고 조회 API는 아직 설계에 없다(화면 API 대응 3.3의 10번). 이 폴더는 shared와 타입만 불러온다.
import type { ExperienceCard } from "@/domain/card/types"
import type { PostingAnalysis, PostUpload } from "@/domain/posting/types"
import type { GroupPostDetail } from "@/features/group-posts/types"
import type { AddPostingResult, CardMatch, DraftResult, GroupNews, HomeData, MyPosting, MyPostingDetail, MyState, PostingGroup, PriorityCard, RequirementResult, Schedule } from "@/features/my-posts/types"
import { NotFoundError } from "@/shared/lib/errors"

import { listCards, listUnverifiedCards } from "./cards-db"
import { KEYWORDS } from "./example-cards"
import { getProfile } from "./profile-db"
import { exampleAnalysis, findPost, listGroups, setInterest, today } from "./db"
import { addEntry, entries, rawEntry, removeEntry, type Entry } from "./user-postings"

/** 상세에만 보이는 값. 예시 공사(시안 8차 "공고 상세")의 공고 내용과 맞는 카드를 옮겼다. 그 밖의 공고는 그룹 공고의 내용을 쓴다. */
const DETAILS: Record<string, Partial<Pick<MyPostingDetail, "applyAt" | "sections" | "keywords" | "unmatched">> & { matches?: [string, string[]][] }> = {
  "p-pub1": {
    applyAt: "기관 채용 홈페이지",
    sections: [
      { title: "주요 업무", items: ["기관 업무 시스템 운영과 유지보수", "정보보안 점검과 전산 자원 관리"] },
      { title: "필수 요건", items: ["정보처리기사 또는 같은 수준의 자격", "Java 기반 웹 시스템 개발 경험"] },
      { title: "우대 사항", items: ["네트워크 운영 경험", "SQL 튜닝 경험"] },
    ],
    keywords: ["Java", "Spring", "SQL", "정보보안", "네트워크"],
    matches: [
      ["1", ["Java", "SQL"]],
      ["5", ["Java", "Spring", "정보보안"]],
      ["2", ["SQL"]],
    ],
    unmatched: ["네트워크 운영 경험"],
  },
}

/** 홈의 그룹 소식(시안 8차 "홈"의 두 줄). 무엇을 소식으로 볼지는 아직 정하지 않아 예시로 둔다. */
const NEWS: { groupId: string; text: string; postId: string | null }[] = [
  { groupId: "be", text: "새 공고 2건", postId: null },
  { groupId: "pub", text: "나래 님이 예시 공사 공고에 댓글을 남겼습니다", postId: "pub1" },
]


/** 내가 아직 속한 그룹의 공고들. 나간 그룹의 공고는 읽지 못한다. */
function readPosts(e: Entry): { group: PostingGroup; post: GroupPostDetail }[] {
  const groups = listGroups()
  return e.refs.flatMap(([groupId, postId]) => {
    const g = groups.find((x) => x.id === groupId)
    if (!g) return []
    try {
      return [{ group: { id: g.id, name: g.name, mark: g.mark, postId }, post: findPost(groupId, postId) }]
    } catch {
      return []
    }
  })
}

function toPosting(e: Entry): MyPosting {
  const found = readPosts(e)
  const body = found[0]?.post
  if (body) e.snapshot = { company: body.company, title: body.title, deadline: structuredClone(body.deadline) }
  const { company, title, deadline } = e.snapshot ?? { company: "", title: "", deadline: { kind: "unknown" as const } }
  return { id: e.id, company, title, deadline, groups: found.map((f) => f.group), decision: e.decision, stage: e.stage, result: e.result }
}

/** 보이는 행: 본체를 읽을 수 있거나 남긴 값이 있다. 둘 다 없으면 속한 적 없는 그룹의 공고다(?mock=no-groups). */
const visible = (e: Entry) => e.snapshot !== null || readPosts(e).length > 0

/** 내 공고 목록. */
export const listMyPostings = (): MyPosting[] => entries().filter(visible).map(toPosting)

/**
 * 요건마다의 결과 흉내(서버의 CardMatching): 카드 태그가 글에 있으면 matched, 아는 키워드가 글에 있는데 카드가 없으면 unmatched,
 * 그 밖에 프로필 값이 글에 있으면 profile, 아니면 unknown.
 */
function requirementResults(analysis: PostingAnalysis, cards: ExperienceCard[], matches: CardMatch[]): RequirementResult[] {
  const profile = getProfile()
  const facts = [...profile.certificates.map((c) => c.name), ...profile.languages.map((l) => l.test), ...profile.educations.flatMap((e) => (e.major ? [e.major] : []))]
  const has = (text: string, word: string) => text.toLowerCase().includes(word.toLowerCase())
  return (["must", "nice"] as const).flatMap((kind) =>
    analysis[kind].map((text) => {
      // 겹친 태그는 요건 글에 나온 순서다(포함 관계는 흉내 내지 않는다)
      const at = (t: string) => text.toLowerCase().indexOf(t.toLowerCase())
      // 서버처럼 요건의 카드는 모두 맞는 카드에도 있고 그 순서를 따른다
      const matched = matches
        .map(({ cardId }) => ({ cardId, overlap: (cards.find((c) => c.id === cardId)?.tags ?? []).filter((t) => has(text, t)).sort((a, b) => at(a) - at(b)) }))
        .filter((c) => c.overlap.length > 0)
      const profileItems = facts.filter((f) => has(text, f))
      const status = matched.length > 0 ? "matched" : KEYWORDS.some((k) => has(text, k)) ? "unmatched" : profileItems.length > 0 ? "profile" : "unknown"
      return { kind, text, status, cards: status === "matched" ? matched : [], profileItems: status === "matched" ? [] : profileItems }
    })
  )
}

/** 내 공고 상세. */
export function getMyPosting(id: string): MyPostingDetail {
  const e = rawEntry(id)
  if (!visible(e)) throw new NotFoundError("공고")
  const posting = toPosting(e)
  const found = readPosts(e)
  const body = found[0]?.post
  const extra = DETAILS[id] ?? {}
  const cards = listCards()
  const keywords = extra.keywords ?? body?.keywords ?? e.own?.keywords ?? []
  // 맞는 카드: 예시 공고는 시안 값, 그 밖에는 공고 키워드와 카드 태그가 겹치는 카드(서버의 CardMatching을 흉내 낸다)
  const matches: CardMatch[] = extra.matches
    ? extra.matches.flatMap(([cardId, overlap]) => {
        const card = cards.find((c) => c.id === cardId)
        return card ? [{ cardId, title: card.title, experience: card.experience, overlap }] : []
      })
    : cards
        .map((c) => ({ cardId: c.id, title: c.title, experience: c.experience, overlap: c.tags.filter((t) => keywords.some((k) => k.toLowerCase() === t.toLowerCase())) }))
        .filter((m) => m.overlap.length > 0)
        .sort((a, b) => b.overlap.length - a.overlap.length)
  const sections = structuredClone(extra.sections ?? body?.sections ?? e.own?.sections ?? [])
  // 분석이 없으면 서버는 맞는 카드를 세지 않는다
  const analysis = exampleAnalysis(sections, keywords)
  return {
    ...posting,
    applyAt: extra.applyAt ?? body?.applyAt ?? e.own?.applyAt ?? null,
    sourceUrl: body?.sourceUrl ?? e.own?.sourceUrl ?? null,
    sections,
    keywords: [...keywords],
    memo: e.memo,
    analysis,
    matches: analysis ? matches : [],
    unmatched: analysis ? [...(extra.unmatched ?? [])] : [],
    requirements: analysis ? requirementResults(analysis, cards, matches) : [],
    cardPriority: priorityOf(e),
    groupStates: found.map(({ group, post }) => ({
      ...group,
      interested: post.me.interested,
      commentCount: post.commentCount,
      disclosure: listGroups().find((g) => g.id === group.id)?.myDisclosure ?? "applied",
    })),
  }
}

/**
 * 그룹 없이 내 공고에 넣기(POST /api/me/postings). 가짜 서버는 원문 주소, 또는 회사명과 공고명이 같은 내 공고를 같은 공고로 본다
 * (서버는 공고키로 본다). 같은 공고가 있으면 409 ALREADY_IN_MY_POSTINGS와 그 공고의 id.
 */
export function addMyPosting(input: PostUpload): AddPostingResult {
  const same = entries()
    .filter(visible)
    .find((e) => {
      const { company, title } = toPosting(e)
      const url = readPosts(e)[0]?.post.sourceUrl ?? e.own?.sourceUrl ?? null
      return (input.sourceUrl !== null && url === input.sourceUrl) || (company === input.company && title === input.title)
    })
  if (same) return { ok: false, error: "ALREADY_IN_MY_POSTINGS", postingId: same.id }
  const { company, title, deadline, applyAt, sourceUrl, sections, keywords } = structuredClone(input)
  return { ok: true, postingId: addEntry({ company, title, deadline }, { applyAt, sourceUrl, sections, keywords }).id }
}

/** 내 지원 상태 바꾸기(PUT /api/me/postings/{postingId}). 단계와 결과는 지원함일 때만 함께 둔다. */
export function updateMyState(id: string, state: MyState): MyPosting {
  const e = rawEntry(id)
  const ok = state.decision === "applied" ? state.stage !== null && state.result !== null : state.stage === null && state.result === null
  if (!ok) throw new Error("INVALID_INPUT: 단계와 결과는 지원함일 때만 함께 넣는다")
  Object.assign(e, { decision: state.decision, stage: state.stage, result: state.result })
  return toPosting(e)
}

// 전형 일정: 예시 공사 필기 하나. 메일을 반영하면 더해진다(mail-db.ts)
let schedules: Schedule[] | null = null
const scheduleList = () => (schedules ??= [{ postingId: "p-pub1", company: "예시 공사", title: "전산직 7급 공채", stage: "test", date: "2026-11-21", time: "10:00" }])

/** 내 전형 일정, 날짜순(GET /api/me/schedules). */
export function listSchedules(): Schedule[] {
  return structuredClone(scheduleList()).sort((a, b) => a.date.localeCompare(b.date) || (a.time ?? "").localeCompare(b.time ?? ""))
}

/** 메일에서 반영한 다음 전형 일정을 넣는다. 같은 공고·단계가 있으면 바꾼다. */
export function addSchedule(schedule: Schedule): void {
  schedules = [...scheduleList().filter((s) => !(s.postingId === schedule.postingId && s.stage === schedule.stage)), structuredClone(schedule)]
}

/** 내 공고에서 빼기(DELETE /api/me/postings/{postingId}): 행과 전형 일정을 지우고 그 공고가 있는 그룹들의 관심을 끈다. */
export function removeMyPosting(id: string): void {
  const e = rawEntry(id)
  for (const [groupId, postId] of e.refs) setInterest(groupId, postId, false)
  schedules = scheduleList().filter((s) => s.postingId !== e.id)
  removeEntry(e.id)
}

/** 나만 보는 메모 저장. */
export function saveMemo(id: string, memo: string): void {
  rawEntry(id).memo = memo
}

/** 홈에 모으는 것. 메일에서 온 변경은 메일 확인함(mail-db.ts)이 센다. */
export function getHome(): Omit<HomeData, "mailChanges"> {
  const groups = listGroups()
  const news: GroupNews[] = NEWS.flatMap((n) => {
    const g = groups.find((x) => x.id === n.groupId)
    return g ? [{ group: { id: g.id, name: g.name, mark: g.mark }, text: n.text, postId: n.postId }] : []
  })
  return { today: today(), postings: listMyPostings(), unverifiedCards: listUnverifiedCards().length, news }
}

/** 우선순위 목록. 지운 카드는 빠진다(서버는 외래 키로 지운다). */
function priorityOf(e: Entry): PriorityCard[] {
  const cards = listCards()
  return (e.priority ?? []).flatMap(({ cardId, reason }) => {
    const card = cards.find((c) => c.id === cardId)
    return card ? [{ cardId, title: card.title, experience: card.experience, reason }] : []
  })
}

/**
 * 우선순위 LLM 초안 흉내(POST …/card-priority/draft): 공고 키워드와 겹치는 태그가 많은 카드부터 5장, 이유는 겹친 키워드다.
 * 카드가 없으면 NO_CARDS, 공고 내용도 키워드도 없으면 NO_POSTING_CONTENT.
 */
export function draftCardPriority(id: string): DraftResult {
  const posting = getMyPosting(id)
  const cards = listCards()
  if (cards.length === 0) return { ok: false, error: "NO_CARDS" }
  if (posting.sections.length === 0 && posting.keywords.length === 0) return { ok: false, error: "NO_POSTING_CONTENT" }
  const same = (a: string, b: string) => a.toLowerCase() === b.toLowerCase()
  rawEntry(id).priority = cards
    .map((c) => ({ cardId: c.id, overlap: c.tags.filter((t) => posting.keywords.some((k) => same(k, t))) }))
    .filter((c) => c.overlap.length > 0)
    .sort((a, b) => b.overlap.length - a.overlap.length)
    .slice(0, 5)
    .map(({ cardId, overlap }) => ({ cardId, reason: `공고 키워드 ${overlap.join(", ")}에 답하는 카드입니다` }))
  return { ok: true, cards: priorityOf(rawEntry(id)) }
}

/** 우선순위를 이 순서로 둔다(PUT …/card-priority). 앞 목록에 있던 카드는 이유가 남는다. */
export function saveCardPriority(id: string, cardIds: string[]): PriorityCard[] {
  const e = rawEntry(id)
  if (cardIds.length > 5 || new Set(cardIds).size !== cardIds.length) throw new Error("INVALID_INPUT: 5장까지, 겹치지 않게")
  e.priority = cardIds.map((cardId) => ({ cardId, reason: e.priority?.find((p) => p.cardId === cardId)?.reason ?? null }))
  return priorityOf(e)
}
