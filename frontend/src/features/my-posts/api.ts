// 홈과 내 공고 화면이 서버와 주고받는 곳. 내 공고 조회와 홈 모음은 아직 설계에 없고(화면 API 대응 3.3의 10, 14번),
// 상태 바꾸기만 PUT /api/me/postings/{postingId}가 있다. 지금은 가짜 서버(mocks/my-db.ts)를 부른다.
import * as db from "@/mocks/my-db"

import type { HomeData, MyPosting, MyPostingDetail, MyState } from "./types"

/** 내 공고 목록과 서버 기준 오늘. 거르기와 검색은 화면에서 한다. */
export async function getMyPostings(): Promise<{ today: string; postings: MyPosting[] }> {
  return { today: db.getHome().today, postings: db.listMyPostings() }
}

/** 내 공고 상세. 없으면 NotFoundError. */
export async function getMyPosting(id: string): Promise<{ today: string; posting: MyPostingDetail }> {
  return { today: db.getHome().today, posting: db.getMyPosting(id) }
}

/** 내 지원 상태를 바꾼다. 단계와 결과는 지원함일 때만 함께 보낸다. */
export async function updateMyState(id: string, state: MyState): Promise<MyPosting> {
  return db.updateMyState(id, state)
}

/** 나만 보는 메모를 저장한다. */
export async function saveMemo(id: string, memo: string): Promise<void> {
  db.saveMemo(id, memo)
}

/** 홈에 보일 것: 내 공고, 확인을 기다리는 카드 수, 메일에서 온 변경, 그룹 소식. */
export async function getHome(): Promise<HomeData> {
  return db.getHome()
}
