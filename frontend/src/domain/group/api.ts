// 그룹을 읽고 바꾸는 곳 가운데 두 기능 이상이 쓰는 것. 주소는 그룹 문서 4절이다.
import * as db from "@/mocks/db"
import { apiPath, mockServer, request } from "@/shared/lib/api"

import type { Disclosure, Group, GroupResponse } from "./types"

/** 서버의 그룹을 화면의 모양으로: id(숫자)를 문자열로 바꾼다. */
export const toGroup = (g: GroupResponse): Group => ({
  ...g,
  id: String(g.id),
  members: g.members.map((m) => ({ ...m, id: String(m.id) })),
  leaderId: g.leaderId === null ? null : String(g.leaderId),
})

/** 내가 속한 그룹들(들어간 순서). 그룹 전환 메뉴와 첫 화면에서 쓴다. */
export async function getMyGroups(): Promise<Group[]> {
  if (mockServer) return db.listGroups()
  return (await request<GroupResponse[]>("GET", "/api/groups")).map(toGroup)
}

/** 이 그룹에 보일 내 공개 범위를 정한다. 멤버 화면과 "공개 범위" 창이 쓴다. */
export async function setMyDisclosure(groupId: string, disclosure: Disclosure): Promise<void> {
  if (mockServer) return db.setMyDisclosure(groupId, disclosure)
  await request("PUT", apiPath`/api/groups/${groupId}/me/disclosure`, { disclosure })
}
