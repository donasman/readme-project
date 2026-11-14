// 내 그룹과 멤버 화면이 서버와 주고받는 곳(그룹 설계 4.1, 화면 API 대응 2.3).
// shortcut: 가짜 서버(mocks/db.ts)를 부른다, 백엔드를 연결하면 함수 안만 /api/groups 호출로 바꾼다(서버에 이미 있는 API다)
import { getMyGroups } from "@/domain/group/api"
import type { Group } from "@/domain/group/types"
import * as db from "@/mocks/db"

/** 내 그룹 목록과 서버 기준 오늘(가장 가까운 마감의 D-day를 센다). */
export async function getGroupList(): Promise<{ today: string; groups: Group[] }> {
  return { today: db.today(), groups: await getMyGroups() }
}

/** 그룹을 만든다. 만든 사람이 그룹장이다. 표시 글자를 비우면 이름 첫 글자를 쓴다. */
export async function createGroup(input: { name: string; mark: string }): Promise<Group> {
  const name = input.name.trim()
  return db.createGroup(name, input.mark.trim() || name.slice(0, 1))
}

/** 초대 링크를 만든다. 토큰은 만들 때만 알 수 있다(서버는 해시만 둔다). */
export async function createInvite(groupId: string): Promise<{ token: string; expiresAt: string }> {
  return db.createInvite(groupId)
}

/** 그룹에서 나간다. 그룹장은 나갈 수 없다. */
export async function leaveGroup(groupId: string): Promise<void> {
  db.leaveGroup(groupId)
}
