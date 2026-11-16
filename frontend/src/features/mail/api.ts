// 메일 확인함이 서버와 주고받는 곳(메일 설계 4.4). 확인함에서 누를 수 없는 메일(이미 반영했거나 지웠다)은 404 MAIL_NOT_FOUND다.
import * as db from "@/mocks/mail-db"
import { apiPath, mockServer, request } from "@/shared/lib/api"
import { formatKstMinute } from "@/shared/lib/date"

import type { Mailbox } from "./types"

/** 확인함. 모두 받은 때의 역순이다. */
export async function getMailbox(): Promise<Mailbox> {
  if (mockServer) return db.getMailbox()
  const box = await request<Mailbox>("GET", "/api/me/mailbox")
  return {
    pending: box.pending.map((m) => ({ ...m, receivedAt: formatKstMinute(m.receivedAt) })),
    added: box.added.map(({ id, from, receivedAt, company, title }) => ({ id, from, receivedAt: formatKstMinute(receivedAt), company, title })),
    forwarding: box.forwarding.map((f) => ({ ...f, receivedAt: formatKstMinute(f.receivedAt) })),
    waiting: box.waiting,
  }
}

/** 메일이 말한 상태를 내 공고에 반영한다. 일정이 있으면 함께 넣는다. */
export async function applyMail(id: string): Promise<void> {
  if (mockServer) return db.applyMail(id)
  await request("POST", apiPath`/api/me/mailbox/${id}/apply`)
}

/** 반영하지 않고(확인 필요), 또는 확인 코드를 쓴 뒤(전달 확인) 확인함에서 뺀다. */
export async function ignoreMail(id: string): Promise<void> {
  if (mockServer) return db.ignoreMail(id)
  await request("POST", apiPath`/api/me/mailbox/${id}/ignore`)
}

/** 메일로 넣은 공고를 내 공고에서 뺀다. */
export async function revertAdded(id: string): Promise<void> {
  if (mockServer) return void db.revertAdded(id)
  await request("POST", apiPath`/api/me/mailbox/${id}/revert`)
}
