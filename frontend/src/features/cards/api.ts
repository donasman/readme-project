// 경험카드 화면이 서버와 주고받는 유일한 곳. 주소는 경험카드 문서 4.1(카드)과 4.2(자료와 추출)다.
import * as db from "@/mocks/cards-db"
import { apiPath, mockServer, request } from "@/shared/lib/api"

import { chunkCount } from "./card-text"
import type { CardDetail, CardInput, CardsData, CardSource, CardTag, ExtractionJob, KeywordSuggestion } from "./types"

// shortcut: 서버가 카드를 뽑은 자료 원문(sourceExcerpt)을 싣지 않아 비워 둔다, 정해지면 응답의 값을 쓴다(화면 API 대응 3.5의 21번)
const withExcerpt = (card: Omit<CardDetail, "sourceExcerpt">): CardDetail => ({ ...card, sourceExcerpt: null })

/** 내 경험카드 목록과 태그. 거르기는 화면에서 한다(카드 수가 적어서). */
export async function getCards(): Promise<CardsData> {
  if (mockServer) return { cards: db.listCards(), tags: db.listCardTags() }
  const [cards, tags] = await Promise.all([request<CardDetail[]>("GET", "/api/cards"), request<CardTag[]>("GET", "/api/cards/tags")])
  return { cards, tags }
}

/** 카드 상세. 없거나 남의 카드면 404 CARD_NOT_FOUND(오류 화면이 "찾을 수 없습니다"를 보인다). */
export async function getCard(id: string): Promise<CardDetail> {
  if (mockServer) return db.getCard(id)
  return withExcerpt(await request("GET", apiPath`/api/cards/${id}`))
}

/** 확인을 기다리는 카드(카드 확인 화면이 한 장씩 보여 준다). */
export async function getUnverifiedCards(): Promise<CardDetail[]> {
  if (mockServer) return db.listUnverifiedCards()
  return (await request<Omit<CardDetail, "sourceExcerpt">[]>("GET", "/api/cards?verified=false")).map(withExcerpt)
}

/** 카드를 고친다. 서버는 태그를 대표 표기로 바꿔 저장하고 검사 결과를 다시 매긴다. */
export async function updateCard(id: string, input: CardInput): Promise<CardDetail> {
  if (mockServer) return db.updateCard(id, input)
  return withExcerpt(await request("PUT", apiPath`/api/cards/${id}`, input))
}

/** 카드를 확인한다. STAR에만 있는 숫자가 근거 숫자로 옮겨진다. */
export async function verifyCard(id: string): Promise<CardDetail> {
  if (mockServer) return db.verifyCard(id)
  return withExcerpt(await request("POST", apiPath`/api/cards/${id}/verify`))
}

export async function deleteCard(id: string): Promise<void> {
  if (mockServer) return db.deleteCard(id)
  await request("DELETE", apiPath`/api/cards/${id}`)
}

// shortcut: 자료와 추출은 아직 가짜 서버를 부른다, 자료를 붙이는 브랜치에서 /api/card-sources로 바꾼다(경험카드 문서 4.2, 6절)

/** 올린 자료(새 것이 앞). */
export async function getSources(): Promise<CardSource[]> {
  return db.listSources()
}

/** 자료를 올린다: 파일 또는 붙여넣은 글. 서버가 글을 읽어 글자 수와 조각 수를 알려 준다. */
export async function uploadSource(input: { file: File } | { text: string }): Promise<{ source: CardSource; chunks: number }> {
  let name: string, chars: number
  if ("text" in input) {
    name = "붙여넣은 글"
    chars = input.text.length
  } else {
    name = input.file.name
    // shortcut: 글 파일만 세고 그 밖의 파일(PDF, DOCX …)은 크기로 어림한다, 서버를 붙이면 파일을 보내고 서버가 센 값을 쓴다
    chars = /\.(txt|md)$/i.test(name) ? (await input.file.text()).length : Math.max(1, Math.round(input.file.size / 2))
  }
  const source = db.addSource(name, chars)
  return { source, chunks: chunkCount(chars) }
}

export async function deleteSource(id: string): Promise<void> {
  db.deleteSource(id)
}

/** 카드 뽑기를 시작한다. 서버가 뒤에서 돌리고, 화면은 getExtractionJob으로 몇 초마다 묻는다. */
export async function startExtraction(source: CardSource): Promise<ExtractionJob> {
  return db.startExtraction(source.id, chunkCount(source.chars))
}

export async function getExtractionJob(jobId: string): Promise<ExtractionJob> {
  return db.getExtractionJob(jobId)
}

/** 태그 입력 후보: 다른 표기를 쳐도 대표 표기를 준다(k8s → Kubernetes). 많아야 10개. */
export async function suggestKeywords(q: string): Promise<KeywordSuggestion[]> {
  if (mockServer) return db.suggestKeywords(q)
  return request("GET", `/api/keywords?q=${encodeURIComponent(q)}`)
}
