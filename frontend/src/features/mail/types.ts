// 메일 확인함의 데이터 모양.
// shortcut: 메일 자동화 전체가 설계에 없어 모양은 프런트가 정했다, 설계가 생기면 맞춘다(화면 API 대응 3.4의 16번)
import type { MyState } from "@/domain/posting/my-state"

/** 지원 상태를 바꾸자는 메일: 눌러야 반영된다. */
export interface MailChange {
  id: string
  from: string
  receivedAt: string
  subject: string
  /** 홈에 보일 짧은 이름. 예: "예시 보안 면접 안내" */
  summary: string
  postingId: string
  company: string
  title: string
  before: MyState
  after: MyState
  /** 근거 문장 */
  quote: string
}

/** 메일에서 읽어 바로 내 공고에 넣은 것. 되돌릴 수 있다. */
export interface MailAdded {
  id: string
  from: string
  receivedAt: string
  note: string
  company: string
  title: string
}

export interface Mailbox {
  pending: MailChange[]
  added: MailAdded[]
}
