// 로그인한 사람을 읽는 곳. 지금은 가짜 서버(mocks/db.ts)를 부르고, 백엔드를 연결하면 GET /api/me 로 바꾼다.
import * as db from "@/mocks/db"

import type { Viewer } from "./types"

/** 지금 보는 사람. 로그인하지 않았으면 null(서버는 401). */
export async function getViewer(): Promise<Viewer | null> {
  return db.viewer()
}
