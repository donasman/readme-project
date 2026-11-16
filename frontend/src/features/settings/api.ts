// 설정 화면이 서버와 주고받는 곳. 내 정보, 표시 이름, 로그아웃, 탈퇴는 서버를 부른다(로그인 문서 4절).
// shortcut: 메일 자동화는 설계에 없어 가짜 서버를 부른다, 설계가 생기면 함수 안만 그 API로 바꾼다(화면 API 대응 3.4의 16번)
import type { MeResponse } from "@/domain/session/types"
import * as db from "@/mocks/account-db"
import { isApiError, mockServer, request, SERVER_ORIGIN } from "@/shared/lib/api"

import type { AccountInfo, MailSettings, WithdrawConfirm, WithdrawInfo } from "./types"

/** 내 정보. 로그인한 계정은 모두 이메일 인증을 마쳤다(로그인 문서 7절 16번). */
export async function getAccount(): Promise<AccountInfo> {
  if (mockServer) return db.getAccount()
  const { name, email, hasPassword, google } = await request<MeResponse>("GET", "/api/me")
  return { name, email, emailVerified: true, hasPassword, google }
}

/**
 * 로그인한 채로 구글 계정을 잇는다(로그인 문서 4.1, 7절 22~25번). 시작 API가 이 세션에 연결할 사람을 적고, 페이지를 서버의 구글 로그인
 * 주소로 옮긴다(fetch가 아니다). 구글을 거쳐 내 정보로 ?google=linked|in-use|has-google|error를 들고 돌아온다.
 * 시작 API를 건너뛰고 바로 옮기면 연결이 아니라 구글 로그인이 된다.
 */
export async function startGoogleLink(): Promise<void> {
  // shortcut: 가짜 서버에는 구글이 없어 연결한 것으로 돌아온다, 가짜 서버를 지우면 이 줄도 지운다
  if (mockServer) return window.location.assign("/settings/account?google=linked")
  await request("POST", "/api/me/google-link")
  window.location.assign(`${SERVER_ORIGIN}/oauth2/authorization/google`)
}

/** 표시 이름을 바꾼다(그룹 멤버에게 보이는 이름). 서버는 앞뒤 공백을 지우고 1~50자를 받는다. */
export async function renameMe(name: string): Promise<void> {
  if (mockServer) return void db.rename(name)
  await request("PUT", "/api/me", { name })
}

/**
 * 로그인한 채로 비밀번호를 바꾼다(PUT /api/me/password, 로그인 문서 4.1). 이 기기는 로그인된 채로 있고 다른 기기는 모두 로그아웃된다.
 * 지금 비밀번호가 틀리면(400 WRONG_PASSWORD) "wrong", 15분 안에 10번 틀렸으면(429 TOO_MANY_ATTEMPTS) "throttled",
 * 비밀번호가 없는 계정이면(409 NO_PASSWORD) "none".
 */
export async function changePassword(currentPassword: string, newPassword: string): Promise<"done" | "wrong" | "throttled" | "none"> {
  if (mockServer) return db.changePassword(currentPassword)
  try {
    await request("PUT", "/api/me/password", { currentPassword, newPassword })
    return "done"
  } catch (error) {
    if (isApiError(error, "WRONG_PASSWORD")) return "wrong"
    if (isApiError(error, "TOO_MANY_ATTEMPTS")) return "throttled"
    if (isApiError(error, "NO_PASSWORD")) return "none"
    throw error
  }
}

/** 로그아웃(POST /api/auth/logout). 로그인하지 않았어도 성공이다. */
export async function logout(): Promise<void> {
  if (mockServer) return db.logout()
  await request("POST", "/api/auth/logout")
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

/** 탈퇴하면 지워질 것의 수와 내가 그룹장인 그룹. */
export async function getWithdrawInfo(): Promise<WithdrawInfo> {
  if (mockServer) return db.getWithdrawInfo()
  return request("GET", "/api/me/withdrawal")
}

/**
 * 탈퇴한다. 확인이 틀리면(400 WRONG_PASSWORD, WRONG_EMAIL) "wrong", 비밀번호를 15분 안에 10번 틀렸으면(429 TOO_MANY_ATTEMPTS) "throttled".
 * 성공하면 서버가 세션을 지워 로그인하지 않은 상태가 된다.
 */
export async function withdraw(confirm: WithdrawConfirm): Promise<"done" | "wrong" | "throttled"> {
  if (mockServer) return db.withdraw("password" in confirm ? confirm.password : confirm.email).ok ? "done" : "wrong"
  try {
    await request("POST", "/api/me/withdraw", confirm)
    return "done"
  } catch (error) {
    if (isApiError(error, "WRONG_PASSWORD", "WRONG_EMAIL")) return "wrong"
    if (isApiError(error, "TOO_MANY_ATTEMPTS")) return "throttled"
    throw error
  }
}
