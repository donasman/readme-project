// 로그인 쪽 화면이 서버와 주고받는 값의 모양.

/** 초대 링크의 내용(GET /api/group-invites/{token}의 본문). 링크를 만든 사람이 계정을 지웠으면 inviterName이 null. */
export interface InvitePreview {
  groupName: string
  mark: string
  inviterName: string | null
  memberCount: number
}

/** 초대 화면이 보는 것. 없거나 지난 링크는 expired. */
export type InviteView = ({ status: "open" } & InvitePreview) | { status: "expired" }

/** 초대 수락 결과. 지난 링크는 서버가 410 INVITE_EXPIRED로 알린다. */
export type AcceptInviteResult = { ok: true; groupId: string } | { ok: false; error: "INVITE_EXPIRED" }
