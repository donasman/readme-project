// 가짜 서버의 메일 확인함: 내 수신 주소로 온 채용 메일에서 읽어 낸 것(시안 9차 "메일 확인함"의 모양).
// 상태를 바꾸는 것은 눌러야 반영되고, 새 공고는 바로 넣는다. 이 폴더는 shared와 타입만 불러온다.
// 지금 상태(before)는 저장하지 않고 내 공고 행(user-postings.ts)에서 읽는다. 9차의 두 메일은 8차 상태에서 이미 반영된 값이라,
// 반영하면 바뀌는 메일로 고쳤다: 예시 보안은 1차 면접 다음(2차 면접), 서류 결과는 서류 진행 중인 예시 핀테크.
import type { MailAdded, MailChange, Mailbox } from "@/features/mail/types"
import { NotFoundError } from "@/shared/lib/errors"

import { updateMyState } from "./my-db"
import { stateOf } from "./user-postings"

type StoredMail = Omit<MailChange, "before">

function createMailbox(): { pending: StoredMail[]; added: MailAdded[] } {
  return {
    pending: [
      {
        id: "m1",
        from: "recruit@security.example",
        receivedAt: "2026-11-12",
        subject: "[예시 보안] 2차 면접 일정 안내",
        summary: "예시 보안 면접 안내",
        postingId: "p-be8",
        company: "예시 보안",
        title: "주니어 서버 개발",
        after: { decision: "applied", stage: "interview_2", result: "in_progress" },
        quote: "1차 면접에 합격하셨습니다. 2차 면접은 11월 17일입니다.",
      },
      {
        id: "m2",
        from: "hr@fintech.example",
        receivedAt: "2026-11-11",
        subject: "[예시 핀테크] 서류 전형 결과 안내",
        summary: "예시 핀테크 서류 결과",
        postingId: "p-be2",
        company: "예시 핀테크",
        title: "서버 개발 인턴",
        after: { decision: "applied", stage: "document", result: "failed" },
        quote: "아쉽게도 이번 전형에서는 모시지 못하게 되었습니다.",
      },
    ],
    added: [{ id: "a1", from: "예시 채용 사이트", receivedAt: "2026-11-12", note: "새 공고로 내 공고에 넣음", company: "예시 클라우드", title: "플랫폼 엔지니어 신입" }],
  }
}

let store: ReturnType<typeof createMailbox> | null = null
const box = () => (store ??= createMailbox())
const copy = <T>(value: T): T => structuredClone(value)

/** 메일 확인함. 바뀌기 전 상태는 지금 내 공고의 상태다. */
export function getMailbox(): Mailbox {
  const { pending, added } = copy(box())
  return { pending: pending.map((m) => ({ ...m, before: stateOf(m.postingId) })), added }
}

/** 홈의 "메일에서 온 변경 N건: …". */
export function mailSummary(): { count: number; first: string | null } {
  const pending = box().pending
  return { count: pending.length, first: pending[0]?.summary ?? null }
}

function take(id: string): StoredMail {
  const found = box().pending.find((m) => m.id === id)
  if (!found) throw new NotFoundError("메일")
  box().pending = box().pending.filter((m) => m !== found)
  return found
}

/** 반영: 내 공고의 지원 상태를 메일이 말한 상태로 바꾼다. */
export function applyMail(id: string): void {
  const m = take(id)
  updateMyState(m.postingId, m.after)
}

/** 무시: 상태는 그대로 두고 확인함에서 뺀다. */
export function ignoreMail(id: string): void {
  take(id)
}

/** 되돌리기: 메일로 넣은 공고를 내 공고에서 뺀다. */
export function revertAdded(id: string): MailAdded {
  const found = box().added.find((a) => a.id === id)
  if (!found) throw new NotFoundError("메일")
  box().added = box().added.filter((a) => a !== found)
  return copy(found)
}
