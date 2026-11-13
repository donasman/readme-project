// 그룹을 읽는 곳. 지금은 가짜 서버(mocks/db.ts)를 부르고, 백엔드를 연결하면 함수 안만 fetch로 바꾼다.
import * as db from "@/mocks/db"

import type { Group } from "./types"

/** 내가 속한 그룹들. 그룹 전환 메뉴와 첫 화면에서 쓴다. */
export async function getMyGroups(): Promise<Group[]> {
  return db.listGroups()
}
