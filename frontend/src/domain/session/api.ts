// 로그인한 사람을 읽는 곳.
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
