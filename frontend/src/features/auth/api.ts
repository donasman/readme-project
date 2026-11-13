// 계정 화면(가입, 이메일 인증, 비밀번호 재설정)이 서버와 주고받는 유일한 곳.
// 지금은 가짜 서버(mocks/db.ts)를 부른다. 서버가 생기면 함수 안만 docs/리드미_설계_로그인_경험카드.md 4.1의
// /api/auth/* 호출로 바꾸고, 함수 이름과 돌려주는 모양은 그대로 둔다.
import * as db from "@/mocks/db"

import type { AcceptInviteResult, InviteView } from "./types"

export type SignupResult = { ok: true } | { ok: false; error: "EMAIL_TAKEN" }

/** 이메일로 가입한다. 확인 메일은 서버가 보낸다. */
export async function signup(input: { name: string; email: string; password: string }): Promise<SignupResult> {
  return db.signup(input.email)
}

/** 가입 확인 메일을 다시 보낸다. 계정이 있는지는 알려 주지 않는다. */
export async function resendVerification(email: string): Promise<void> {
  db.resendVerification(email)
}

/** 메일의 가입 확인 링크를 쓴다. 지났거나 이미 쓴 링크면 expired. */
export async function verifyEmail(token: string | null): Promise<"done" | "expired"> {
  return db.verifyEmail(token)
}

/** 재설정 메일을 보낸다. 계정이 있는지는 알려 주지 않는다. */
export async function requestPasswordReset(email: string): Promise<void> {
  db.requestPasswordReset(email)
}

/** 재설정 링크를 아직 쓸 수 있는지. */
export async function checkResetToken(token: string | null): Promise<boolean> {
  return db.checkResetToken(token)
}

/** 새 비밀번호로 바꾼다. 바꾸면 그 계정의 다른 로그인이 모두 풀린다. */
export async function resetPassword(token: string | null, password: string): Promise<"done" | "expired"> {
  return db.resetPassword(token, password)
}

/** 초대 링크의 내용(그룹 이름, 만든 사람, 멤버 수). 없거나 지난 링크는 expired. 로그인하지 않아도 읽는다. */
export async function getInvite(token: string): Promise<InviteView> {
  return db.getInvite(token)
}

/** 초대를 받아 그룹에 들어간다. 이미 멤버면 그대로 들어간다. */
export async function acceptInvite(token: string): Promise<AcceptInviteResult> {
  return db.acceptInvite(token)
}
