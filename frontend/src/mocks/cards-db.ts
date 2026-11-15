// 가짜 서버의 경험카드 쪽: 카드, 올린 자료, 카드 뽑기 작업, 태그 대표 표기. 백엔드를 연결하면 이 파일도 지운다.
// 서버가 할 일(카드 검사, 조각 나누기, 뽑기 진행)도 흉내 낸다. 이 폴더는 shared와 타입(import type)만 불러온다.
import type { CardDetail, CardInput, CardSource, CardTag, ExperienceCard, ExtractionJob, KeywordSuggestion } from "@/features/cards/types"
import { NotFoundError } from "@/shared/lib/errors"

import { createExampleCards, createExampleSources, createExtractionPool, KEYWORD_ALIASES, KEYWORDS } from "./example-cards"
import { EXAMPLE_TODAY } from "./example-data"


let cardStore: CardDetail[] | null = null
const cards = () => (cardStore ??= createExampleCards())
let sourceStore: CardSource[] | null = null
const sources = () => (sourceStore ??= createExampleSources())
let pool = createExtractionPool()
const jobs = new Map<string, ExtractionJob & { startedAt: number }>()
let nextId = 100

function rawCard(id: string): CardDetail {
  const found = cards().find((c) => c.id === id)
  if (!found) throw new NotFoundError("카드")
  return found
}

const summaryOf = ({ id, title, period, org, role, tags, summary, usableFor, verified }: CardDetail): ExperienceCard => ({
  id, title, period, org, role, tags, summary, usableFor, verified,
})

/** 서버의 카드 검사(설계 문서 6절 check_cards의 일부): 요약 60자 초과, 근거 숫자 없음, 결과의 확인 필요 숫자. */
function checkCard(card: CardDetail): CardDetail["issues"] {
  const issues: CardDetail["issues"] = []
  if (card.summary.length > 60) issues.push({ level: "경고", field: "요약", message: `요약이 ${card.summary.length}자입니다(60자 이내)` })
  const flagged = [...card.star.result.matchAll(/\[확인 필요: ([^\]]+)\]/g)].map((m) => m[1])
  if (flagged.length) issues.push({ level: "경고", field: "결과", message: `근거 숫자에 없는 숫자가 있습니다: ${flagged.join(", ")}` })
  if (card.facts.length === 0) issues.push({ level: "경고", field: "근거 숫자", message: "근거 숫자가 없습니다" })
  return issues
}

/** 내 경험카드 목록(GET /api/cards). */
export const listCards = (): ExperienceCard[] => cards().map((c) => structuredClone(summaryOf(c)))

/** 내 카드에 쓰인 태그와 카드 수(GET /api/cards/tags). 처음 나온 순서로 돌려준다. */
export function listCardTags(): CardTag[] {
  const counts = new Map<string, number>()
  for (const c of cards()) for (const tag of c.tags) counts.set(tag, (counts.get(tag) ?? 0) + 1)
  return [...counts].map(([name, count]) => ({ name, count }))
}

/** 카드 상세(GET /api/cards/{id}). 없으면 NotFoundError. */
export const getCard = (id: string): CardDetail => structuredClone(rawCard(id))

/** 확인 전 카드(GET /api/cards?verified=false), 목록 순서대로. */
export const listUnverifiedCards = (): CardDetail[] => cards().filter((c) => !c.verified).map((c) => structuredClone(c))

/** 카드 고치기(PUT /api/cards/{id}). 검사 결과를 다시 매긴다. */
export function updateCard(id: string, input: CardInput): CardDetail {
  const card = rawCard(id)
  Object.assign(card, structuredClone(input))
  card.issues = checkCard(card)
  return structuredClone(card)
}

// 서버 쪽 숫자 규칙. 화면의 card-text.ts와 같은 규칙이지만, 가짜 서버는 features를 불러오지 않으므로 따로 둔다.
const NUM = /(?<![A-Za-z+\d.])\d+(?:[.,]\d+)?(?:초|회|%|건|명|배|분|개)?(?![초회%건명배분개당\d]|[.,]\d)/g

/** star에만 있는 숫자를 facts로 옮긴다: 그 숫자가 든 문장을 근거 숫자 한 줄로 더한다. [확인 필요] 숫자는 옮기지 않는다. */
function moveStarNumbers(card: CardDetail) {
  const known = new Set(card.facts.join(" ").match(NUM) ?? [])
  for (const part of Object.values(card.star)) {
    for (const sentence of part.replace(/\[확인 필요: [^\]]+\]/g, "").split(/(?<=[.!?])\s+/)) {
      const numbers: string[] = sentence.match(NUM) ?? []
      const fresh = numbers.filter((n) => !known.has(n))
      if (fresh.length === 0) continue
      card.facts.push(sentence.trim().replace(/\.$/, ""))
      for (const n of fresh) known.add(n)
    }
  }
}

/** 카드 확인(POST /api/cards/{id}/verify). star에만 있는 숫자를 facts로 옮기고 추출 메모는 지운다. */
export function verifyCard(id: string): CardDetail {
  const card = rawCard(id)
  moveStarNumbers(card)
  card.verified = true
  card.extractionNotes = []
  card.issues = checkCard(card)
  return structuredClone(card)
}

/** 카드 지우기(DELETE /api/cards/{id}). */
export function deleteCard(id: string): void {
  rawCard(id)
  cardStore = cards().filter((c) => c.id !== id)
}

/** 올린 자료(새 것이 앞). */
export const listSources = (): CardSource[] => structuredClone(sources())

/** 자료 올리기. 서버가 글을 읽어 글자 수를 센다(파일은 저장하지 않는다). */
export function addSource(name: string, chars: number): CardSource {
  const source: CardSource = { id: String(nextId++), name, createdAt: EXAMPLE_TODAY, chars, cardCount: null }
  sources().unshift(source)
  return structuredClone(source)
}

/** 자료 지우기. 만든 카드는 남고 자료와의 연결만 빈다. */
export function deleteSource(id: string): void {
  sourceStore = sources().filter((s) => s.id !== id)
  for (const c of cards()) if (c.sourceId === id) Object.assign(c, { sourceId: null, sourceName: null, sourceExcerpt: null })
}

const CHUNK_MS = 1200

/** 카드 뽑기 시작. 조각 하나에 1.2초씩 걸리는 것처럼 흉내 낸다. */
export function startExtraction(sourceId: string, totalChunks: number): ExtractionJob {
  const source = sources().find((s) => s.id === sourceId)
  if (!source) throw new NotFoundError("자료")
  const job = { id: String(nextId++), sourceId, totalChunks, doneChunks: 0, cardsSoFar: 0, status: "RUNNING" as const, newCards: 0, skippedSameAs: [], startedAt: Date.now() }
  jobs.set(job.id, job)
  return copyJob(job)
}

const copyJob = ({ startedAt, ...job }: ExtractionJob & { startedAt: number }): ExtractionJob => (void startedAt, structuredClone(job))

/** 뽑기 작업 상태. 화면이 몇 초마다 묻는다. 끝나는 순간 카드가 "확인 필요"로 목록에 들어간다. */
export function getExtractionJob(jobId: string): ExtractionJob {
  const job = jobs.get(jobId)
  if (!job) throw new NotFoundError("작업")
  if (job.status === "DONE") return copyJob(job)
  const done = Math.min(job.totalChunks, Math.floor((Date.now() - job.startedAt) / CHUNK_MS))
  const willMake = pool.length
  job.doneChunks = done
  job.cardsSoFar = Math.ceil((willMake * done) / job.totalChunks)
  if (done >= job.totalChunks) {
    const source = sources().find((s) => s.id === job.sourceId)
    const made = pool.map((c) => ({ ...c, id: String(nextId++), sourceId: job.sourceId, sourceName: source?.name ?? null }))
    cardStore = [...made, ...cards()]
    job.skippedSameAs = ["로그인 기능 직접 구현", ...(made.length === 0 ? createExtractionPool().map((c) => c.title) : [])]
    pool = []
    job.newCards = made.length
    job.cardsSoFar = made.length
    job.status = "DONE"
    if (source) source.cardCount = made.length
  }
  return copyJob(job)
}

/** 태그 입력 후보(GET /api/keywords?q=). 다른 표기를 쳐도 대표 표기를 준다. */
export function suggestKeywords(q: string): KeywordSuggestion[] {
  const query = q.trim().toLowerCase()
  if (!query) return []
  const out: KeywordSuggestion[] = []
  const alias = KEYWORD_ALIASES[query]
  if (alias) out.push({ label: alias, note: `${q.trim()}의 대표 표기` })
  for (const k of KEYWORDS) if (k.toLowerCase().includes(query) && k !== alias) out.push({ label: k, note: null })
  return out.slice(0, 5)
}
