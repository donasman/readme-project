// 설정 화면의 데이터 모양. 내 정보는 GET /api/me에서 만들고, 탈퇴는 GET /api/me/withdrawal 그대로다(로그인 문서 4.1).
// shortcut: 메일 자동화는 설계에 없어 모양은 프런트가 정했다, 설계가 생기면 맞춘다(화면 API 대응 3.4의 16번)
import type { Member } from "@/domain/group/types"

export interface AccountInfo {
  name: string
  email: string
  emailVerified: boolean
  hasPassword: boolean
  /** 연결한 구글 계정. 없으면 null. 연결할 때 구글이 이메일을 주지 않았으면 email이 null(GET /api/me의 google) */
  google: { email: string | null } | null
}

export interface MailSettings {
  /** 내 수신 주소. 남이 추측할 수 없게 만든다. 수신 도메인이 없는 서버면 null */
  inboundAddress: string | null
  /** 여기 적힌 주소에서 온 메일만 받는다 */
  senders: string[]
}

/** 탈퇴하면 지워질 것의 수와 내가 그룹장인 그룹(GET /api/me/withdrawal). */
export interface WithdrawInfo {
  cards: number
  sources: number
  postings: number
  groups: number
  /** 그룹마다 나 말고 멤버 수와 그룹장을 이어받을 사람(가장 먼저 들어온 멤버). 혼자인 그룹은 successor가 null이고 탈퇴하면 지워진다 */
  leads: { id: string; name: string; others: number; successor: Member | null }[]
}

/** 탈퇴의 확인: 비밀번호가 있는 계정은 비밀번호, 없는 계정(구글로만 가입)은 계정의 이메일. */
export type WithdrawConfirm = { password: string } | { email: string }
