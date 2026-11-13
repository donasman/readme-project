// 설정 화면이 서버와 주고받는 곳. 지금은 가짜 서버(mocks/account-db.ts)를 부른다.
import * as db from "@/mocks/account-db"

import type { AccountInfo, MailSettings, WithdrawInfo } from "./types"

export async function getAccount(): Promise<AccountInfo> {
  return db.getAccount()
}

/** 표시 이름을 바꾼다(그룹 멤버에게 보이는 이름). */
export async function renameMe(name: string): Promise<void> {
  db.rename(name)
}

/** 로그아웃(POST /api/auth/logout). */
export async function logout(): Promise<void> {
  db.logout()
}

export async function getMailSettings(): Promise<MailSettings> {
  return db.getMailSettings()
}

export async function renewInboundAddress(): Promise<void> {
  db.renewInbound()
}

export async function addSender(email: string): Promise<void> {
  db.addSender(email)
}

export async function removeSender(email: string): Promise<void> {
  db.removeSender(email)
}

export async function getWithdrawInfo(): Promise<WithdrawInfo> {
  return db.getWithdrawInfo()
}

/** 탈퇴한다. 비밀번호로 확인한다. */
export async function withdraw(password: string): Promise<boolean> {
  return db.withdraw(password).ok
}
