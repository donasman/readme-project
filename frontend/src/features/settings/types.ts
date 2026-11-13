// 설정 화면의 데이터 모양. 읽기는 GET /api/me뿐이고 나머지는 아직 설계에 없다(화면 API 대응 3.4의 16~18번).

export interface AccountInfo {
  name: string
  email: string
  emailVerified: boolean
  hasPassword: boolean
  /** 연결한 구글 계정. 없으면 null(GET /api/me의 providers에는 이메일이 없다: 3.4의 17번) */
  google: { email: string } | null
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
