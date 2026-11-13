// 로그인 쪽 화면이 서버와 주고받는 값의 모양.

/** 초대 링크의 내용(화면 API 대응 3.1의 1번). 없거나 지난 링크는 expired. */
export type InviteView =
  | { status: "open"; groupId: string; groupName: string; mark: string; inviterName: string; memberCount: number }
  | { status: "expired" }

/** 초대 수락 결과. 지난 링크는 서버가 410 INVITE_EXPIRED로 알린다. */
export type AcceptInviteResult = { ok: true; groupId: string } | { ok: false; error: "INVITE_EXPIRED" }
