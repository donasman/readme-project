// 메일 확인함의 데이터 모양(메일 설계 4.4). 받은 때는 화면에 보일 "YYYY-MM-DD HH:mm"이다.
import type { MyState, Stage } from "@/domain/posting/my-state"

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
  /** 지금 내 상태. 내 공고에 없는 공고면 null */
  before: MyState | null
  after: MyState
  /** 반영하면 함께 넣을 다음 전형 일정. 없으면 null */
  schedule: { stage: Stage; date: string; time: string | null } | null
  /** 근거 문장(메일에 그대로 있는 것). 없으면 null */
  quote: string | null
}

/** 메일에서 읽어 바로 내 공고에 넣은 것. 되돌릴 수 있다. */
export interface MailAdded {
  id: string
  from: string
  receivedAt: string
  company: string
  title: string
}

/** Gmail 자동 전달을 켤 때 오는 확인 메일. 코드를 Gmail 설정에 넣어야 전달이 시작된다. */
export interface MailForwarding {
  id: string
  receivedAt: string
  subject: string
  /** 메일에서 꺼낸 확인 코드. 못 꺼냈으면 null */
  code: string | null
}

export interface Mailbox {
  pending: MailChange[]
  added: MailAdded[]
  forwarding: MailForwarding[]
  /** 분류를 기다리는 메일 수 */
  waiting: number
}
