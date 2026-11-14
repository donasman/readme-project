// 메일 확인함이 서버와 주고받는 곳.
// shortcut: 메일 자동화 API는 설계에 없어 가짜 서버(mocks/mail-db.ts)를 부른다, 설계가 생기면 함수 안만 그 API로 바꾼다(화면 API 대응 3.4의 16번)
import * as db from "@/mocks/mail-db"

import type { Mailbox } from "./types"

export async function getMailbox(): Promise<Mailbox> {
  return db.getMailbox()
}

/** 메일이 말한 상태를 내 공고에 반영한다. */
export async function applyMail(id: string): Promise<void> {
  db.applyMail(id)
}

/** 반영하지 않고 확인함에서 뺀다. */
export async function ignoreMail(id: string): Promise<void> {
  db.ignoreMail(id)
}

/** 메일로 넣은 공고를 되돌린다. */
export async function revertAdded(id: string): Promise<void> {
  db.revertAdded(id)
}
