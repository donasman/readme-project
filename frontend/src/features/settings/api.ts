// 설정 화면이 서버와 주고받는 곳. 읽기(GET /api/me)와 로그아웃만 설계에 있다.
// shortcut: 내 정보 고치기, 메일 자동화, 탈퇴는 설계에 없어 가짜 서버(mocks/account-db.ts)를 부른다, 설계가 생기면 함수 안만 그 API로 바꾼다(화면 API 대응 3.4의 16~18번)
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
