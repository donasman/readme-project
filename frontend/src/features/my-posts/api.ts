// 홈과 내 공고 화면이 서버와 주고받는 곳. 주소는 공고 문서 4.2(내 공고)와 4.5(그룹 소식)다.
import { mailSummary } from "@/mocks/mail-db"
import * as db from "@/mocks/my-db"
import type { PostUpload } from "@/domain/posting/types"
import { apiPath, isApiError, mockServer, request } from "@/shared/lib/api"

import type { AddPostingResult, GroupNews, HomeData, MyPosting, MyPostingDetail, MyState } from "./types"

/** 내 공고 목록과 서버 기준 오늘. 거르기, 검색, 정렬, 지원 현황의 숫자는 화면에서 한다. */
export async function getMyPostings(): Promise<{ today: string; postings: MyPosting[] }> {
  if (mockServer) return { today: db.getHome().today, postings: db.listMyPostings() }
  return request("GET", "/api/me/postings")
}

/** 서버의 상세: 메모가 없으면 null이고, 맞는 카드는 아직 없다(공고 문서 8절 15번, 17번). */
type MyPostingDetailResponse = Omit<MyPostingDetail, "memo" | "matches" | "unmatched"> & { memo: string | null }

/** 내 공고 상세. 내 공고에 없으면 404 POSTING_NOT_FOUND(오류 화면이 "찾을 수 없습니다"를 보인다). */
export async function getMyPosting(id: string): Promise<{ today: string; posting: MyPostingDetail }> {
  if (mockServer) return { today: db.getHome().today, posting: db.getMyPosting(id) }
  const { today, posting } = await request<{ today: string; posting: MyPostingDetailResponse }>("GET", apiPath`/api/me/postings/${id}`)
  // shortcut: 서버가 공고와 카드를 아직 맞추지 않아 맞는 카드가 비어 있다, 맞추기가 생기면 응답의 값을 쓴다(화면 API 대응 3.3의 13번)
  return { today, posting: { ...posting, memo: posting.memo ?? "", matches: [], unmatched: [] } }
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

/** 홈에 보일 것: 내 공고(GET /api/me/postings), 그룹 소식(GET /api/me/group-activity), 확인을 기다리는 카드 수(GET /api/cards), 메일에서 온 변경. */
export async function getHome(): Promise<HomeData> {
  if (mockServer) return { ...db.getHome(), mailChanges: mailSummary() }
  const [{ today, postings }, news, unverified] = await Promise.all([
    getMyPostings(),
    request<GroupNews[]>("GET", "/api/me/group-activity"),
    // 확인을 기다리는 카드 수. 카드 수가 적어 목록을 받아 센다(기능끼리 불러오지 않으려 cards의 api.ts 대신 바로 부른다)
    request<unknown[]>("GET", "/api/cards?verified=false"),
  ])
  // shortcut: 메일에서 온 변경은 설계에 없어 가짜 서버를 부른다, 메일 자동화가 생기면 바꾼다(화면 API 대응 3.4의 16번)
  return { today, postings, news, unverifiedCards: unverified.length, mailChanges: mailSummary() }
}
