// 경험카드 화면이 데이터를 가져오는 유일한 곳.
// 지금은 백엔드 연결 전이라 가짜 서버(mocks/db.ts)를 부른다. 서버가 생기면 함수 안만
// GET /api/cards, GET /api/cards/tags 호출로 바꾸고, 함수 이름과 돌려주는 모양(types.ts)은 그대로 둔다.
import * as db from "@/mocks/db"

import type { CardsData } from "./types"

/** 내 경험카드 목록과 태그. 거르기는 화면에서 한다(카드 수가 적어서). */
export async function getCards(): Promise<CardsData> {
  return { cards: db.listCards(), tags: db.listCardTags() }
}
