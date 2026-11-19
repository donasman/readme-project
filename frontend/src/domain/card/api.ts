// 카드 한 장 읽기(경험카드 문서 4.1). 경험카드 화면과 내 공고 상세의 카드 미리 보기 창이 함께 쓴다.
import * as db from "@/mocks/cards-db"
import { apiPath, mockServer, request } from "@/shared/lib/api"

import type { CardDetail } from "./types"

/** 카드 상세. 없거나 남의 카드면 404 CARD_NOT_FOUND(오류 화면이 "찾을 수 없습니다"를 보인다). */
export async function getCard(id: string): Promise<CardDetail> {
  if (mockServer) return db.getCard(id)
  return request<CardDetail>("GET", apiPath`/api/cards/${id}`)
}
