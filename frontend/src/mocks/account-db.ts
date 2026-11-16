// 가짜 서버의 설정 쪽: 내 정보, 메일 자동화(수신 주소, 보내는 주소), 탈퇴. 시안 9차의 값이다.
// 내 정보 고치기, 메일 자동화, 탈퇴는 아직 설계에 없다(화면 API 대응 3.4의 16~18번). 이 폴더는 shared와 타입만 불러온다.
import type { AccountInfo, MailSettings, WithdrawInfo } from "@/features/settings/types"

import { listCards, listSources } from "./cards-db"
import { listGroups, mockFlag, signOut, viewer } from "./db"
import { listMyPostings } from "./my-db"

/** 내 정보. 구글은 ?mock=no-google이면 연결 전으로, 비밀번호는 ?mock=no-password이면 없는 계정(구글로만 가입)으로 본다. */
export function getAccount(): AccountInfo {
  const me = viewer()
  return {
    name: me?.name ?? "",
    email: "name@example.com",
    emailVerified: true,
    hasPassword: !mockFlag("no-password"),
    google: mockFlag("no-google") ? null : { email: "name@gmail.example" },
  }
}

export { renameViewer as rename, signOut as logout } from "./db"

/** 비밀번호 바꾸기(PUT /api/me/password). 가짜 서버의 지금 비밀번호는 서버의 예시 계정과 같은 readme1234다. */
export function changePassword(currentPassword: string): "done" | "wrong" | "none" {
  if (mockFlag("no-password")) return "none"
  return currentPassword === "readme1234" ? "done" : "wrong"
}

let inbound = "daon-7f3k9x2m@in.readme.example"
let senders = ["name@gmail.example"]

export const getMailSettings = (): MailSettings => ({ inboundAddress: inbound, senders: [...senders] })

/** 수신 주소를 새로 만든다. 앞의 주소로 온 메일은 더 받지 않는다. */
export function renewInbound(): string {
  inbound = `daon-${Math.random().toString(36).slice(2, 10)}@in.readme.example`
  return inbound
}

export function addSender(email: string): void {
  const key = email.trim().toLowerCase()
  if (!senders.includes(key)) senders = [...senders, key]
}

export function removeSender(email: string): void {
  senders = senders.filter((s) => s !== email)
}

/** 탈퇴하면 지워질 것과 내가 그룹장인 그룹. */
export function getWithdrawInfo(): WithdrawInfo {
  const me = viewer()
  const groups = listGroups()
  return {
    cards: listCards().length,
    sources: listSources().length,
    postings: listMyPostings().length,
    groups: groups.length,
    leads: groups
      .filter((g) => g.leaderId === me?.id)
      .map((g) => {
        // 서버처럼 나 말고 가장 먼저 들어온 멤버가 이어받는다
        const next = g.members.filter((m) => m.id !== me?.id).sort((a, b) => a.joinedAt.localeCompare(b.joinedAt))[0]
        return { id: g.id, name: g.name, others: g.members.length - 1, successor: next ? { id: next.id, name: next.name } : null }
      }),
  }
}

/** 탈퇴(비밀번호로 확인). 가짜 서버는 비밀번호를 비교하지 않는다. */
export function withdraw(password: string): { ok: true } | { ok: false; error: "WRONG_PASSWORD" } {
  if (!password) return { ok: false, error: "WRONG_PASSWORD" }
  signOut()
  return { ok: true }
}
