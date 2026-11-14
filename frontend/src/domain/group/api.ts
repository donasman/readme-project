// 그룹을 읽고 바꾸는 곳 가운데 두 기능 이상이 쓰는 것.
// shortcut: 가짜 서버(mocks/db.ts)를 부른다, 백엔드를 연결하면 함수 안만 fetch로 바꾼다(서버에 이미 있는 API다)
import * as db from "@/mocks/db"

import type { Disclosure, Group } from "./types"

/** 내가 속한 그룹들. 그룹 전환 메뉴와 첫 화면에서 쓴다. */
export async function getMyGroups(): Promise<Group[]> {
  return db.listGroups()
}

/** 이 그룹에 보일 내 공개 범위를 정한다(PUT /api/groups/{groupId}/me/disclosure). 멤버 화면과 "공개 범위" 창이 쓴다. */
export async function setMyDisclosure(groupId: string, disclosure: Disclosure): Promise<void> {
  db.setMyDisclosure(groupId, disclosure)
}
