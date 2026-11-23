// 가짜 서버의 내 공고 쪽: 관심을 켜거나 직접 넣은 공고와 내 지원 상태, 메모, 맞는 카드, 홈의 모음.
// 내 지원 상태와 메모는 내 공고 행(user-postings.ts)에 있고, 그룹 공고와 메일 확인함도 같은 행을 읽고 쓴다.
// 공고 본체(회사, 공고명, 마감, 내용)는 그룹 공고(db.ts)에서 그때그때 읽는다. 그룹에서 마감일을 넣으면 여기도 바뀐다.
// 내 공고 조회 API는 아직 설계에 없다(화면 API 대응 3.3의 10번). 이 폴더는 shared와 타입만 불러온다.
import type { ExperienceCard, Star } from "@/domain/card/types"
import type { PostingAnalysis, PostUpload } from "@/domain/posting/types"
import type { GroupPostDetail } from "@/features/group-posts/types"
import type {
  AddPostingResult,
  CardMatch,
  DraftResult,
  GroupNews,
  HomeData,
  MatchResult,
  MyPosting,
  MyPostingDetail,
  MyState,
  PostingGroup,
  PriorityCard,
  Question,
  QuestionType,
  RequirementResult,
  Schedule,
} from "@/features/my-posts/types"
import { NotFoundError } from "@/shared/lib/errors"

import { getCard, listCards, listUnverifiedCards } from "./cards-db"
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
    questions: questionsOf(e, analysis),
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
 * LLM에 넘기는 입력의 열쇠. 서버처럼 이것이 앞 답 때와 같으면 LLM을 부르지 않고 그 답을 다시 준다.
 * 카드는 서버가 넘기는 칸만 id 순서로 본다(확인 여부, 어디서, 쓸 수 있는 문항은 넘기지 않아 바뀌어도 같은 입력이다).
 */
function inputKey(posting: MyPostingDetail, questions: unknown[] = []): string {
  const cards = listCards()
    .map((c) => getCard(c.id))
    .sort((a, b) => Number(a.id) - Number(b.id))
    .map(({ id, title, experience, period, role, summary, star: s, tags }) => [id, title, experience, period, role, summary, [s.situation, s.task, s.action, s.result, s.obstacle, s.learning], tags])
  return JSON.stringify([posting.company, posting.title, posting.sections, posting.keywords, questions, cards])
}

/**
 * 우선순위 LLM 초안 흉내(POST …/card-priority/draft): 공고 키워드와 겹치는 태그가 많은 카드부터 5장, 이유는 겹친 키워드다.
 * 카드가 없으면 NO_CARDS, 공고 내용도 키워드도 없으면 NO_POSTING_CONTENT. 입력이 앞 초안 때와 같으면 기다리지 않고 그 초안을 다시 둔다(reused).
 * 고른 카드가 없으면 서버처럼 기억하지 않아 다음에 다시 만든다.
 */
// 초안을 만드는 동안 목록이 막힌 화면이 보이도록 잠깐 기다린다. 진짜 서버(LLM)는 몇 초 걸린다
const DRAFT_MS = 600

export async function draftCardPriority(id: string): Promise<DraftResult> {
  const e = rawEntry(id)
  const posting = getMyPosting(id)
  const cards = listCards()
  if (cards.length === 0) return { ok: false, error: "NO_CARDS" }
  if (posting.sections.length === 0 && posting.keywords.length === 0) return { ok: false, error: "NO_POSTING_CONTENT" }
  const key = inputKey(posting)
  if (e.lastDraft?.key === key) {
    e.priority = structuredClone(e.lastDraft.priority)
    return { ok: true, cards: priorityOf(e), reused: true }
  }
  await new Promise((resolve) => setTimeout(resolve, DRAFT_MS))
  const same = (a: string, b: string) => a.toLowerCase() === b.toLowerCase()
  e.priority = cards
    .map((c) => ({ cardId: c.id, overlap: c.tags.filter((t) => posting.keywords.some((k) => same(k, t))) }))
    .filter((c) => c.overlap.length > 0)
    .sort((a, b) => b.overlap.length - a.overlap.length)
    .slice(0, 5)
    .map(({ cardId, overlap }) => ({ cardId, reason: `공고 키워드 ${overlap.join(", ")}에 답하는 카드입니다` }))
  if (e.priority.length > 0) e.lastDraft = { key, priority: structuredClone(e.priority) }
  return { ok: true, cards: priorityOf(e), reused: false }
}

/** 우선순위를 이 순서로 둔다(PUT …/card-priority). 앞 목록에 있던 카드는 이유가 남는다. */
export function saveCardPriority(id: string, cardIds: string[]): PriorityCard[] {
  const e = rawEntry(id)
  if (cardIds.length > 5 || new Set(cardIds).size !== cardIds.length) throw new Error("INVALID_INPUT: 5장까지, 겹치지 않게")
  e.priority = cardIds.map((cardId) => ({ cardId, reason: e.priority?.find((p) => p.cardId === cardId)?.reason ?? null }))
  return priorityOf(e)
}

/** 자기소개서 문항(문항 매칭 문서 4절). 저장 전이면 공고 분석의 문항이고 id가 null이다. 지운 카드는 빠진다(서버는 외래 키로 지운다). */
function questionsOf(e: Entry, analysis: PostingAnalysis | null): Question[] {
  if (!e.questions) return (analysis?.questions ?? []).map((q) => ({ id: null, text: q.text, limit: q.limit, type: null, cards: [] }))
  const cards = listCards()
  return e.questions.map((q) => ({
    ...q,
    cards: q.cards.flatMap((c) => {
      const card = cards.find((x) => x.id === c.cardId)
      return card ? [{ ...c, title: card.title, experience: card.experience }] : []
    }),
  }))
}

/** 문항에 둘 카드 수(QD1). 화면의 question-match.ts와 같은 규칙이다(가짜 서버는 features를 불러오지 않는다) */
const maxQuestionCards = (limit: number | null) => (limit !== null && limit < 500 ? 1 : 2)

let nextQuestionId = 1

/** 문항 목록을 통째로 둔다(PUT …/questions). id가 있으면 그 문항의 유형과 카드가 남고, 없으면 새 문항, 빠진 문항은 지운다. */
export function saveQuestions(id: string, list: Pick<Question, "id" | "text" | "limit">[]): Question[] {
  const e = rawEntry(id)
  const ok =
    list.length <= 10 &&
    list.every(
      (q) =>
        q.text.trim() !== "" &&
        q.text.length <= 1000 &&
        (q.limit === null || (Number.isInteger(q.limit) && q.limit >= 1 && q.limit <= 10000)) &&
        (q.id === null || e.questions?.some((s) => s.id === q.id))
    )
  if (!ok) throw new Error("INVALID_INPUT: 문항 10개까지, 글 1~1,000자, 글자 수 1~10,000이나 없음, 이 공고의 내 문항")
  e.questions = list.map((q) => {
    const before = e.questions?.find((s) => s.id === q.id)
    return { id: before?.id ?? `q${nextQuestionId++}`, text: q.text, limit: q.limit, type: before?.type ?? null, cards: before?.cards ?? [] }
  })
  return questionsOf(e, null)
}

// 문항 글로 유형을 흉내 낸다. 앞의 것이 먼저다(지원 동기와 포부를 함께 물으면 지원동기)
const TYPE_WORDS: [QuestionType, RegExp][] = [
  ["MOTIVE", /지원\s?동기/],
  ["FUTURE", /포부/],
  ["FAILURE", /실패/],
  ["TEAM", /협업|갈등|소통/],
  ["PROBLEM", /문제|개선/],
  ["GOAL", /목표|도전/],
  ["GROWTH", /성장|노력|자기\s?개발/],
  ["VALUES", /가치관|성격|장단점/],
]

/** 유형마다 살릴 칸(문항 매칭 리서치 3절) */
const EMPHASIS: Record<QuestionType, (keyof Star)[]> = {
  JOB: ["action", "result"],
  PROBLEM: ["situation", "action", "result"],
  GOAL: ["task", "obstacle", "result"],
  TEAM: ["action", "obstacle", "result"],
  FAILURE: ["obstacle", "learning"],
  GROWTH: ["action", "learning"],
  VALUES: ["learning", "action"],
  MOTIVE: [],
  FUTURE: [],
}

/**
 * 문항 매칭 LLM 흉내(POST …/questions/match): 문항과 카드가 있으면 저장 전 문항을 먼저 저장하고, 문항 글로 유형을, 공고 키워드와 겹치는 태그가 많은
 * 카드부터 문항끼리 겹치지 않게 골라 앞 결과를 바꾼다(모자라면 다시 쓴다). 지원동기·포부는 카드를 고르지 않는다.
 * 공고, 문항(id, 앞뒤 빈칸을 뗀 글, 글자 수), 카드가 앞 매칭 때와 같으면 기다리지 않고 그때 고른 유형과 카드를 다시 둔다(reused).
 * 모든 문항에 카드가 없으면 서버처럼 기억하지 않아 다음에 다시 고른다.
 */
export async function matchQuestions(id: string): Promise<MatchResult> {
  const posting = getMyPosting(id)
  const e = rawEntry(id)
  // 서버처럼 문항 없음과 카드 없음을 먼저 보고, 고를 수 있을 때만 저장 전 문항을 저장한다
  if (posting.questions.length === 0) return { ok: false, error: "NO_QUESTIONS" }
  const same = (a: string, b: string) => a.toLowerCase() === b.toLowerCase()
  const ranked = listCards()
    .map((c) => ({ cardId: c.id, overlap: c.tags.filter((t) => posting.keywords.some((k) => same(k, t))) }))
    .sort((a, b) => b.overlap.length - a.overlap.length)
  if (ranked.length === 0) return { ok: false, error: "NO_CARDS" }
  if (!e.questions) saveQuestions(id, posting.questions)
  const questions = e.questions ?? []
  // 저장한 뒤의 문항 id로 본다. 처음 누를 때 저장된 문항도 다음에 같은 입력이 된다
  const key = inputKey(posting, questions.map((q) => [q.id, q.text.trim(), q.limit]))
  const last = e.lastMatch
  if (last?.key === key) {
    questions.forEach((q, i) => Object.assign(q, structuredClone(last.picks[i])))
    return { ok: true, questions: questionsOf(e, null), reused: true }
  }
  await new Promise((resolve) => setTimeout(resolve, DRAFT_MS))
  const used = new Set<string>()
  for (const q of questions) {
    const type = TYPE_WORDS.find(([, words]) => words.test(q.text))?.[0] ?? "JOB"
    const picks = type === "MOTIVE" || type === "FUTURE" ? [] : [...ranked.filter((c) => !used.has(c.cardId)), ...ranked.filter((c) => used.has(c.cardId))].slice(0, maxQuestionCards(q.limit))
    for (const c of picks) used.add(c.cardId)
    q.type = type
    q.cards = picks.map(({ cardId, overlap }) => ({
      cardId,
      reason: overlap.length > 0 ? `공고 키워드 ${overlap.join(", ")}와 이어지는 장면입니다` : "이 문항에 답할 장면이 있는 카드입니다",
      emphasis: EMPHASIS[type],
    }))
  }
  if (questions.some((q) => q.cards.length > 0)) e.lastMatch = { key, picks: structuredClone(questions.map(({ type, cards }) => ({ type, cards }))) }
  return { ok: true, questions: questionsOf(e, null), reused: false }
}

/** 문항 하나의 카드를 이 순서로 둔다(PUT …/questions/{questionId}/cards). 앞에 있던 카드는 이유와 살릴 칸이 남는다. */
export function setQuestionCards(id: string, questionId: string, cardIds: string[]): Question[] {
  const e = rawEntry(id)
  const q = e.questions?.find((x) => x.id === questionId)
  if (!q) throw new NotFoundError("문항")
  const mine = listCards()
  if (cardIds.length > maxQuestionCards(q.limit) || new Set(cardIds).size !== cardIds.length || !cardIds.every((c) => mine.some((m) => m.id === c))) {
    throw new Error("INVALID_INPUT: 문항의 카드 수까지, 겹치지 않게, 내 카드만")
  }
  q.cards = cardIds.map((cardId) => q.cards.find((c) => c.cardId === cardId) ?? { cardId, reason: null, emphasis: [] })
  return questionsOf(e, null)
}
