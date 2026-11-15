// 설정 화면의 데이터 모양. 내 정보는 GET /api/me에서 만든다. 탈퇴는 GET /api/me/withdrawal의 일부다(서버는 그룹마다 id와 이어받을 사람을 더 준다, 로그인 문서 7절 19번).
// shortcut: 메일 자동화는 설계에 없어 모양은 프런트가 정했다, 설계가 생기면 맞춘다(화면 API 대응 3.4의 16번)

export interface AccountInfo {
  name: string
  email: string
  emailVerified: boolean
  hasPassword: boolean
  /** 연결한 구글 계정. 없으면 null. 연결할 때 구글이 이메일을 주지 않았으면 email이 null(GET /api/me의 google) */
  google: { email: string | null } | null
}

export interface MailSettings {
  /** 내 수신 주소. 남이 추측할 수 없게 만든다 */
  inboundAddress: string
  /** 여기 적힌 주소에서 온 메일만 받는다 */
  senders: string[]
}

/** 탈퇴하면 지워질 것의 수와 내가 그룹장인 그룹(그룹장이 없어질 때는 그룹 설계 9절에 남은 결정). */
export interface WithdrawInfo {
  cards: number
  sources: number
  postings: number
  groups: number
  leads: { name: string; others: number }[]
}
