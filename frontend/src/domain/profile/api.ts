// 기본 프로필 읽기. 설정의 기본 프로필과 카드 뽑기 결과가 함께 쓴다(프로필 설계 5절).
import * as db from "@/mocks/profile-db"
import { mockServer, request } from "@/shared/lib/api"

import type { Profile } from "./types"

export async function getProfile(): Promise<Profile> {
  if (mockServer) return db.getProfile()
  return request("GET", "/api/me/profile")
}
