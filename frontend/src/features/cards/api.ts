// 경험카드 화면이 서버와 주고받는 유일한 곳. 주소는 경험카드 문서 4.1(카드)과 4.2(자료와 추출)다.
import * as db from "@/mocks/cards-db"
import { apiPath, isApiError, mockServer, request, tooLargeMessage } from "@/shared/lib/api"
import { sentence } from "@/shared/lib/korean"

import { chunkCount } from "./card-text"
import type { CardDetail, CardInput, CardsData, CardSource, CardTag, ExtractionJob, KeywordSuggestion } from "./types"


/** 내 경험카드 목록과 태그. 거르기는 화면에서 한다(카드 수가 적어서). */
export async function getCards(): Promise<CardsData> {
  if (mockServer) return { cards: db.listCards(), tags: db.listCardTags() }
  const [cards, tags] = await Promise.all([request<CardDetail[]>("GET", "/api/cards"), request<CardTag[]>("GET", "/api/cards/tags")])
  return { cards, tags }
}

/** 카드 상세. 없거나 남의 카드면 404 CARD_NOT_FOUND(오류 화면이 "찾을 수 없습니다"를 보인다). */
export async function getCard(id: string): Promise<CardDetail> {
  if (mockServer) return db.getCard(id)
  return request<CardDetail>("GET", apiPath`/api/cards/${id}`)
}

/** 확인을 기다리는 카드(카드 확인 화면이 한 장씩 보여 준다). */
export async function getUnverifiedCards(): Promise<CardDetail[]> {
  if (mockServer) return db.listUnverifiedCards()
  return request<CardDetail[]>("GET", "/api/cards?verified=false")
}

/** 카드를 직접 만든다. 직접 만든 카드도 확인 전이다. 서버는 태그를 대표 표기로 바꿔 저장하고 검사 결과를 매긴다. */
export async function createCard(input: CardInput): Promise<CardDetail> {
  if (mockServer) return db.createCard(input)
  return request<CardDetail>("POST", "/api/cards", input)
}

/** 카드를 고친다. 서버는 태그를 대표 표기로 바꿔 저장하고 검사 결과를 다시 매긴다. */
export async function updateCard(id: string, input: CardInput): Promise<CardDetail> {
  if (mockServer) return db.updateCard(id, input)
  return request<CardDetail>("PUT", apiPath`/api/cards/${id}`, input)
}

/** 카드를 확인한다. STAR에만 있는 숫자가 근거 숫자로 옮겨진다. */
export async function verifyCard(id: string): Promise<CardDetail> {
  if (mockServer) return db.verifyCard(id)
  return request<CardDetail>("POST", apiPath`/api/cards/${id}/verify`)
}

export async function deleteCard(id: string): Promise<void> {
  if (mockServer) return db.deleteCard(id)
  await request("DELETE", apiPath`/api/cards/${id}`)
}

/** 서버의 자료(경험카드 문서 4.2). 붙여넣은 글은 이름이 null이고, 조각 수(parts)와 가장 최근 추출 작업이 함께 온다. */
interface SourceResponse {
  id: string
  name: string | null
  createdAt: string
  chars: number
  parts: number
  /** 이 자료에서 뽑아 남아 있는 카드 수. 아직 뽑지 않았으면 null */
  cardCount: number | null
}

const toSource = (s: SourceResponse): CardSource => ({ id: s.id, name: s.name ?? "붙여넣은 글", createdAt: s.createdAt, chars: s.chars, cardCount: s.cardCount })

/** 올린 자료(새 것이 앞). */
export async function getSources(): Promise<CardSource[]> {
  if (mockServer) return db.listSources()
  return (await request<SourceResponse[]>("GET", "/api/card-sources")).map(toSource)
}

/** 올리기 결과: 읽은 자료와 조각 수, 또는 칸 아래에 알릴 이유(너무 큼, 받지 않는 형식, 읽지 못함, 글자 없음). */
export type UploadResult = { ok: true; source: CardSource; chunks: number } | { ok: false; message: string }

/** 자료를 올린다: 파일 또는 붙여넣은 글. 서버가 글을 읽어 글자 수와 조각 수를 알려 준다. 이미 올린 자료면 그 자료를 준다. */
export async function uploadSource(input: { file: File } | { text: string }): Promise<UploadResult> {
  if (mockServer) {
    const name = "text" in input ? "붙여넣은 글" : input.file.name
    const chars = "text" in input ? input.text.length : /\.(txt|md)$/i.test(name) ? (await input.file.text()).length : Math.max(1, Math.round(input.file.size / 2))
    const source = db.addSource(name, chars)
    return { ok: true, source, chunks: chunkCount(chars) }
  }
  let body: FormData | { text: string } = input as { text: string }
  if ("file" in input) {
    body = new FormData()
    body.append("file", input.file)
  }
  try {
    const { source } = await request<{ source: SourceResponse; duplicate: boolean }>("POST", "/api/card-sources", body)
    return { ok: true, source: toSource(source), chunks: source.parts }
  } catch (error) {
    const tooLarge = tooLargeMessage(error)
    if (tooLarge) return { ok: false, message: tooLarge }
    if (isApiError(error, "SOURCE_UNSUPPORTED_TYPE", "SOURCE_UNREADABLE", "SOURCE_NO_TEXT", "SOURCE_EMPTY")) return { ok: false, message: sentence(error.message) }
    throw error
  }
}

/** 자료를 지운다. 만든 카드는 남는다. */
export async function deleteSource(id: string): Promise<void> {
  if (mockServer) return db.deleteSource(id)
  await request("DELETE", apiPath`/api/card-sources/${id}`)
}

/** 카드 뽑기를 시작한다. 서버가 뒤에서 돌리고, 화면은 getExtractionJob으로 몇 초마다 묻는다. 서버에 아직 없으면 null. */
export async function startExtraction(source: CardSource): Promise<ExtractionJob | null> {
  if (mockServer) return db.startExtraction(source.id, chunkCount(source.chars))
  // shortcut: 서버에 추출 시작·작업 상태 API가 아직 없다(경험카드 문서 4.2), 생기면 POST /api/card-sources/{id}/extract와 GET /api/card-extractions/{jobId}로 바꾼다
  return null
}

export async function getExtractionJob(jobId: string): Promise<ExtractionJob> {
  return db.getExtractionJob(jobId)
}

/** 태그 입력 후보: 다른 표기를 쳐도 대표 표기를 준다(k8s → Kubernetes). 많아야 10개. */
export async function suggestKeywords(q: string): Promise<KeywordSuggestion[]> {
  if (mockServer) return db.suggestKeywords(q)
  return request("GET", `/api/keywords?q=${encodeURIComponent(q)}`)
}
