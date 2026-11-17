// 로그인한 사람을 읽는 곳과 로그인 화면·설정이 함께 쓰는 재설정 메일.
import * as db from "@/mocks/db"
import { ApiError, mockServer, request } from "@/shared/lib/api"

import type { MeResponse, Viewer } from "./types"

/** 지금 보는 사람(GET /api/me). 로그인하지 않았으면 null(서버는 401). */
export async function getViewer(): Promise<Viewer | null> {
  if (mockServer) return db.viewer()
  try {
    const { id, name } = await request<MeResponse>("GET", "/api/me")
    return { id, name }
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

/** 재설정 메일을 보낸다. 계정이 있는지, 메일 제한(1분에 한 번, 24시간에 5번)에 걸렸는지는 알려 주지 않는다(로그인 문서 4.1). */
export async function requestPasswordReset(email: string): Promise<void> {
  if (mockServer) return db.requestPasswordReset(email)
  await request("POST", "/api/auth/password-reset/request", { email })
}
