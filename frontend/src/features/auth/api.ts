// 계정 화면(로그인, 가입, 이메일 인증, 비밀번호 재설정, 초대)이 서버와 주고받는 유일한 곳. 주소와 오류 이름은 docs/리드미_설계_로그인.md 4절이다.
import type { Group } from "@/domain/group/types"
import * as db from "@/mocks/db"
import { apiPath, isApiError, mockServer, request, SERVER_ORIGIN } from "@/shared/lib/api"

import type { AcceptInviteResult, InvitePreview, InviteView } from "./types"

/** 로그인 결과. 막히면 서버의 오류 이름과 칸 아래에 보일 글. */
export type LoginResult = { ok: true } | { ok: false; code: string; message: string }

/** 이메일·비밀번호로 로그인한다. 틀림(401), 인증 전(403), 시도가 너무 많음(429), 받지 않는 값(400)은 던지지 않고 돌려준다. */
export async function login(email: string, password: string): Promise<LoginResult> {
  if (mockServer) return db.signIn()
  try {
    await request("POST", "/api/auth/login", { email, password })
    return { ok: true }
  } catch (error) {
    if (isApiError(error, "INVALID_CREDENTIALS", "EMAIL_NOT_VERIFIED", "TOO_MANY_ATTEMPTS", "INVALID_INPUT")) {
      return { ok: false, code: error.code, message: error.message }
    }
    throw error
  }
}

/**
 * 구글 로그인: fetch가 아니라 서버의 구글 로그인 주소로 페이지를 옮긴다. 끝나면 서버가 홈(/)으로, 실패하면 /login?error=google로 보낸다.
 * 돌아갈 화면은 홈의 sessionLoader가 옮긴다(domain/session/return-to.ts).
 * 구글에 등록한 돌아올 주소가 서버의 것이라 서버 주소로 바로 간다(로그인 문서 7절 10번).
 */
export function startGoogleLogin(): void {
  // shortcut: 가짜 서버에는 구글 로그인이 없어 아무 일도 하지 않는다, 가짜 서버를 지우면 이 줄도 지운다
  if (mockServer) return
  window.location.assign(`${SERVER_ORIGIN}/oauth2/authorization/google`)
}

export type SignupResult = { ok: true } | { ok: false; error: "EMAIL_TAKEN" }

/** 이메일로 가입한다. 확인 메일은 서버가 보낸다. 인증을 마친 계정이 그 이메일을 쓰면 EMAIL_TAKEN(409). */
export async function signup(input: { name: string; email: string; password: string }): Promise<SignupResult> {
  if (mockServer) return db.signup(input.email)
  try {
    await request("POST", "/api/auth/signup", input)
    return { ok: true }
  } catch (error) {
    if (isApiError(error, "EMAIL_TAKEN")) return { ok: false, error: "EMAIL_TAKEN" }
    throw error
  }
}

/** 가입 확인 메일을 다시 보낸다. 계정이 있는지, 메일 제한(1분에 한 번)에 걸렸는지는 알려 주지 않는다. */
export async function resendVerification(email: string): Promise<void> {
  if (mockServer) return db.resendVerification(email)
  await request("POST", "/api/auth/verify-email/resend", { email })
}

/** 메일 링크의 토큰을 보낸다. 토큰이 없거나, 지났거나 이미 쓴 링크(410 LINK_EXPIRED)면 expired. */
async function sendToken(path: string, body: { token: string | null; password?: string }): Promise<"done" | "expired"> {
  if (!body.token) return "expired"
  try {
    await request("POST", path, body)
    return "done"
  } catch (error) {
    if (isApiError(error, "LINK_EXPIRED")) return "expired"
    throw error
  }
}

/** 메일의 가입 확인 링크를 쓴다. 지났거나 이미 쓴 링크면 expired. */
export async function verifyEmail(token: string | null): Promise<"done" | "expired"> {
  if (mockServer) return db.verifyEmail(token)
  return sendToken("/api/auth/verify-email", { token })
}

/** 재설정 링크를 아직 쓸 수 있는지. 링크를 쓰지는 않는다. */
export async function checkResetToken(token: string | null): Promise<boolean> {
  if (mockServer) return db.checkResetToken(token)
  return (await sendToken("/api/auth/password-reset/check", { token })) === "done"
}

/** 새 비밀번호로 바꾼다. 바꾸면 그 계정의 다른 로그인이 모두 풀린다. */
export async function resetPassword(token: string | null, password: string): Promise<"done" | "expired"> {
  if (mockServer) return db.resetPassword(token, password)
  return sendToken("/api/auth/password-reset/confirm", { token, password })
}

/** 초대 링크의 내용(그룹 이름, 만든 사람, 멤버 수). 없거나(404) 만료·취소된(410) 링크는 expired. 로그인하지 않아도 읽는다. */
export async function getInvite(token: string): Promise<InviteView> {
  if (mockServer) return db.getInvite(token)
  try {
    const invite = await request<InvitePreview>("GET", apiPath`/api/group-invites/${token}`)
    return { status: "open", ...invite }
  } catch (error) {
    if (isApiError(error, "INVITE_NOT_FOUND", "INVITE_EXPIRED")) return { status: "expired" }
    throw error
  }
}

/** 초대를 받아 그룹에 들어간다. 이미 멤버면 링크가 지났어도 그대로 들어간다. */
export async function acceptInvite(token: string): Promise<AcceptInviteResult> {
  if (mockServer) return db.acceptInvite(token)
  try {
    const group = await request<Group>("POST", apiPath`/api/group-invites/${token}/accept`)
    return { ok: true, groupId: group.id }
  } catch (error) {
    if (isApiError(error, "INVITE_NOT_FOUND", "INVITE_EXPIRED")) return { ok: false, error: "INVITE_EXPIRED" }
    throw error
  }
}
