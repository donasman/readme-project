// 경험카드 화면이 서버와 주고받는 유일한 곳(docs/리드미_설계_로그인_경험카드.md 4.2, 4.3). 함수 이름과
// 돌려주는 모양(types.ts)은 서버를 붙여도 그대로 둔다.
// shortcut: 가짜 서버(mocks/cards-db.ts)를 부른다, 서버가 생기면 함수 안만 /api/cards, /api/card-sources,
// /api/card-extractions, /api/keywords 호출로 바꾼다(docs/리드미_화면_API_대응.md 2.2)
import * as db from "@/mocks/cards-db"

import { chunkCount } from "./card-text"
import type { CardDetail, CardInput, CardsData, CardSource, ExtractionJob, KeywordSuggestion } from "./types"

/** 내 경험카드 목록과 태그. 거르기는 화면에서 한다(카드 수가 적어서). */
export async function getCards(): Promise<CardsData> {
  return { cards: db.listCards(), tags: db.listCardTags() }
}

/** 카드 상세. 없으면 NotFoundError. */
export async function getCard(id: number): Promise<CardDetail> {
  return db.getCard(id)
}

/** 확인을 기다리는 카드(카드 확인 화면이 한 장씩 보여 준다). */
export async function getUnverifiedCards(): Promise<CardDetail[]> {
  return db.listUnverifiedCards()
}

export async function updateCard(id: number, input: CardInput): Promise<CardDetail> {
  return db.updateCard(id, input)
}

export async function verifyCard(id: number): Promise<CardDetail> {
  return db.verifyCard(id)
}

export async function deleteCard(id: number): Promise<void> {
  db.deleteCard(id)
}

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

export async function deleteSource(id: number): Promise<void> {
  db.deleteSource(id)
}

/** 카드 뽑기를 시작한다. 서버가 뒤에서 돌리고, 화면은 getExtractionJob으로 몇 초마다 묻는다. */
export async function startExtraction(source: CardSource): Promise<ExtractionJob> {
  return db.startExtraction(source.id, chunkCount(source.chars))
}

export async function getExtractionJob(jobId: number): Promise<ExtractionJob> {
  return db.getExtractionJob(jobId)
}

/** 태그 입력 후보. */
export async function suggestKeywords(q: string): Promise<KeywordSuggestion[]> {
  return db.suggestKeywords(q)
}
