// 내 그룹과 멤버 화면의 데이터 모양. 그룹 자체는 domain/group/types.ts에 있다.
import type { Member } from "@/domain/group/types"

/** 만든 초대 링크와 쓸 수 있는 마지막 날("YYYY-MM-DD"). 링크는 만들 때만 알 수 있다(서버는 토큰의 해시만 둔다). */
export interface CreatedInvite {
  link: string
  expiresAt: string
}

/** 쓸 수 있는 초대 링크 한 줄(GET /api/groups/{groupId}/invites). 링크 주소는 없다(만들 때만 보인다). */
export interface LiveInvite {
  id: string
  /** 만든 사람. 계정을 지웠으면 null */
  createdBy: Member | null
  /** 만든 때 "YYYY-MM-DD HH:mm"(한국 시각) */
  createdAt: string
  /** 쓸 수 있는 마지막 날 "YYYY-MM-DD" */
  expiresAt: string
}
