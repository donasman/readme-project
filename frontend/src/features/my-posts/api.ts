// 홈과 내 공고 화면이 서버와 주고받는 곳. 주소는 공고 문서 4.2(내 공고)와 4.5(그룹 소식), 메일 설계 4.4·4.5(메일에서 온 변경, 전형 일정)다.
import { mailSummary } from "@/mocks/mail-db"
import { listCards } from "@/mocks/cards-db"
import * as db from "@/mocks/my-db"
import type { PostUpload } from "@/domain/posting/types"
import { apiPath, isApiError, mockServer, request } from "@/shared/lib/api"

import type { AddPostingResult, CardChoice, DraftResult, GroupNews, HomeData, MyPosting, MyPostingDetail, MyState, PriorityCard, Schedule } from "./types"

/** 내 공고 목록과 서버 기준 오늘. 거르기, 검색, 정렬, 지원 현황의 숫자는 화면에서 한다. */
export async function getMyPostings(): Promise<{ today: string; postings: MyPosting[] }> {
  if (mockServer) return { today: db.getHome().today, postings: db.listMyPostings() }
  return request("GET", "/api/me/postings")
}

/** 서버의 상세: 메모가 없으면 null이다. 공고 분석(analysis)이 없으면 맞는 카드도 비어 온다(공고 문서 4.7, 4.8). */
type MyPostingDetailResponse = Omit<MyPostingDetail, "memo"> & { memo: string | null }

/** 내 공고 상세. 내 공고에 없으면 404 POSTING_NOT_FOUND(오류 화면이 "찾을 수 없습니다"를 보인다). */
export async function getMyPosting(id: string): Promise<{ today: string; posting: MyPostingDetail }> {
  if (mockServer) return { today: db.getHome().today, posting: db.getMyPosting(id) }
  const { today, posting } = await request<{ today: string; posting: MyPostingDetailResponse }>("GET", apiPath`/api/me/postings/${id}`)
  return { today, posting: { ...posting, memo: posting.memo ?? "" } }
}

/**
 * 그룹 없이 내 공고에 넣는다(POST /api/me/postings, 공고 문서 4.2). 본문은 그룹에 올리기와 같고 지원 상태는 미정으로 시작한다.
 * 이미 내 공고에 있으면 409 ALREADY_IN_MY_POSTINGS와 그 공고의 id. 같은 공고를 남이 먼저 올렸으면 회사명과 마감은 그 사람의 값이다.
 */
export async function addMyPosting(input: PostUpload): Promise<AddPostingResult> {
  if (mockServer) return db.addMyPosting(input)
  try {
    const { posting } = await request<{ posting: Pick<MyPosting, "id"> }>("POST", "/api/me/postings", input)
    return { ok: true, postingId: posting.id }
  } catch (error) {
    if (isApiError(error, "ALREADY_IN_MY_POSTINGS")) return { ok: false, error: "ALREADY_IN_MY_POSTINGS", postingId: String(error.body.postingId) }
    throw error
  }
}

/** 내 지원 상태를 바꾼다. 단계와 결과는 지원함일 때만 함께 보낸다. 메모는 건드리지 않는다. */
export async function updateMyState(id: string, state: MyState): Promise<void> {
  if (mockServer) return void db.updateMyState(id, state)
  await request("PUT", apiPath`/api/me/postings/${id}`, state)
}

/** 나만 보는 메모를 저장한다. 비우면 지워진다. 상태는 건드리지 않는다. */
export async function saveMemo(id: string, memo: string): Promise<void> {
  if (mockServer) return db.saveMemo(id, memo)
  await request("PUT", apiPath`/api/me/postings/${id}/memo`, { memo })
}

/** 홈에 보일 것: 내 공고(GET /api/me/postings), 그룹 소식(GET /api/me/group-activity), 확인을 기다리는 카드 수(GET /api/cards), 메일에서 온 변경(GET /api/me/mailbox). */
export async function getHome(): Promise<HomeData> {
  if (mockServer) return { ...db.getHome(), mailChanges: mailSummary() }
  const [{ today, postings }, news, unverified, mailbox] = await Promise.all([
    getMyPostings(),
    request<GroupNews[]>("GET", "/api/me/group-activity"),
    // 확인을 기다리는 카드 수. 카드 수가 적어 목록을 받아 센다(기능끼리 불러오지 않으려 cards의 api.ts 대신 바로 부른다)
    request<unknown[]>("GET", "/api/cards?verified=false"),
    // 메일에서 온 변경: 확인함의 확인 필요 수와 첫 이름(메일 설계 5절). 같은 까닭으로 mail의 api.ts 대신 바로 부른다
    request<{ pending: { summary: string }[] }>("GET", "/api/me/mailbox"),
  ])
  const mailChanges = { count: mailbox.pending.length, first: mailbox.pending[0]?.summary ?? null }
  return { today, postings, news, unverifiedCards: unverified.length, mailChanges }
}

/** 내 공고에서 뺀다(DELETE /api/me/postings/{postingId}). 지원 상태, 메모, 전형 일정이 지워지고 내 그룹들에서 켠 관심이 꺼진다. */
export async function removeMyPosting(id: string): Promise<void> {
  if (mockServer) return db.removeMyPosting(id)
  await request("DELETE", apiPath`/api/me/postings/${id}`)
}

/** 내 전형 일정 전부, 날짜순(GET /api/me/schedules). 달력과 내 공고 빼기 창이 쓴다. */
export async function getSchedules(): Promise<Schedule[]> {
  if (mockServer) return db.listSchedules()
  return request("GET", "/api/me/schedules")
}

/**
 * 경험카드 우선순위 LLM 초안(POST /api/me/postings/{postingId}/card-priority/draft, 카드 우선순위 문서 4절). 앞 목록을 바꾼다.
 * 카드 없음(409 NO_CARDS), 공고 내용 없음(409 NO_POSTING_CONTENT), 이번 달 한도(429 LLM_LIMIT_REACHED)는 패널에 알리고, 나머지(LLM 실패 등)는 던진다.
 */
export async function draftCardPriority(id: string): Promise<DraftResult> {
  if (mockServer) return db.draftCardPriority(id)
  try {
    const { cards } = await request<{ cards: PriorityCard[] }>("POST", apiPath`/api/me/postings/${id}/card-priority/draft`)
    return { ok: true, cards }
  } catch (error) {
    for (const code of ["NO_CARDS", "NO_POSTING_CONTENT", "LLM_LIMIT_REACHED"] as const) {
      if (isApiError(error, code)) return { ok: false, error: code }
    }
    throw error
  }
}

/** 경험카드 우선순위를 이 순서로 저장한다(PUT …/card-priority). 앞 목록에 있던 카드는 고른 이유가 남는다. */
export async function saveCardPriority(id: string, cardIds: string[]): Promise<PriorityCard[]> {
  if (mockServer) return db.saveCardPriority(id, cardIds)
  const { cards } = await request<{ cards: PriorityCard[] }>("PUT", apiPath`/api/me/postings/${id}/card-priority`, { cardIds: cardIds.map(Number) })
  return cards
}

/** 우선순위에 더할 수 있는 내 카드 전부(GET /api/cards). 기능끼리 불러오지 않으려 cards의 api.ts 대신 바로 부른다(getHome과 같다). */
export async function getCardChoices(): Promise<CardChoice[]> {
  const cards = mockServer ? listCards() : await request<{ id: string; title: string; experience: string }[]>("GET", "/api/cards")
  return cards.map((c) => ({ cardId: c.id, title: c.title, experience: c.experience }))
}
