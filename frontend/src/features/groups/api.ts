// 내 그룹과 멤버 화면이 서버와 주고받는 곳(그룹 문서 4절, 화면 API 대응 2.3).
import { getMyGroups, toGroup } from "@/domain/group/api"
import type { Group, GroupResponse } from "@/domain/group/types"
import * as db from "@/mocks/db"
import { apiPath, mockServer, request } from "@/shared/lib/api"
import { seoulToday } from "@/shared/lib/date"
import { paths } from "@/shared/lib/paths"
import { firstChar } from "@/shared/lib/utils"

/** 내 그룹 목록과 오늘(가장 가까운 마감의 D-day를 센다). 서버는 그룹 목록에 오늘을 싣지 않아 한국 날짜로 센다. */
export async function getGroupList(): Promise<{ today: string; groups: Group[] }> {
  return { today: mockServer ? db.today() : seoulToday(), groups: await getMyGroups() }
}

/** 그룹을 만든다. 만든 사람이 그룹장이다. 표시 글자를 비우면 이름 첫 글자를 쓴다. */
export async function createGroup(input: { name: string; mark: string }): Promise<Group> {
  const name = input.name.trim()
  const mark = input.mark.trim() || firstChar(name)
  if (mockServer) return db.createGroup(name, mark)
  return toGroup(await request<GroupResponse>("POST", "/api/groups", { name, mark }))
}

/** 만든 초대 링크와 쓸 수 있는 마지막 날("YYYY-MM-DD"). 링크는 만들 때만 알 수 있다(서버는 토큰의 해시만 둔다). */
export interface CreatedInvite {
  link: string
  expiresAt: string
}

export async function createInvite(groupId: string): Promise<CreatedInvite> {
  if (mockServer) {
    const { token, expiresAt } = db.createInvite(groupId)
    return { link: `${window.location.origin}${paths.invite(token)}`, expiresAt }
  }
  const invite = await request<CreatedInvite>("POST", apiPath`/api/groups/${groupId}/invites`)
  // 만료 시각은 +09:00이 붙은 ISO 8601이라 앞 10자가 한국 날짜다
  return { link: invite.link, expiresAt: invite.expiresAt.slice(0, 10) }
}

/** 그룹에서 나간다. 그룹장은 나갈 수 없다(화면이 버튼을 보이지 않는다. 서버는 409 LEADER_CANNOT_LEAVE). */
export async function leaveGroup(groupId: string): Promise<void> {
  if (mockServer) return db.leaveGroup(groupId)
  await request("DELETE", apiPath`/api/groups/${groupId}/me`)
}
