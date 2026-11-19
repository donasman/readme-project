// 설정 화면이 서버와 주고받는 곳. 내 정보, 표시 이름, 로그아웃, 탈퇴는 로그인 문서 4절, 기본 프로필은 프로필 설계 5절, 메일 자동화는 메일 설계 4.2다.
import type { Profile, ProfileInput } from "@/domain/profile/types"
import type { MeResponse } from "@/domain/session/types"
import * as db from "@/mocks/account-db"
import * as profileDb from "@/mocks/profile-db"
import * as transcriptDb from "@/mocks/transcript-db"
import { ApiError, isApiError, mockServer, request, SERVER_ORIGIN } from "@/shared/lib/api"

import { exampleTranscript } from "./grade-example"
import type { AccountInfo, MailSettings, Transcript, WithdrawConfirm, WithdrawInfo } from "./types"

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

/** 메일 자동화 설정. 처음 부르면 서버가 수신 주소를 만들고 계정 이메일을 보내는 주소에 넣는다. */
export async function getMailSettings(): Promise<MailSettings> {
  if (mockServer) return db.getMailSettings()
  return request("GET", "/api/me/mail-settings")
}

/** 수신 주소를 새로 만든다(옛 주소로 온 메일은 받지 않는다). */
export async function renewInboundAddress(): Promise<void> {
  if (mockServer) return void db.renewInbound()
  await request("POST", "/api/me/mail-settings/inbound-address")
}

/** 보내는 주소 더하기 결과: 형식이 틀렸거나(400 INVALID_INPUT) 이미 10개면(409 TOO_MANY_SENDERS) 칸 아래에 보일 서버의 글. */
export type AddSenderResult = { ok: true } | { ok: false; message: string }

/** 보내는 주소를 더한다. 이미 있으면 그대로 둔다. */
export async function addSender(email: string): Promise<AddSenderResult> {
  if (mockServer) {
    db.addSender(email)
    return { ok: true }
  }
  try {
    await request("POST", "/api/me/mail-settings/senders", { email: email.trim() })
    return { ok: true }
  } catch (error) {
    if (isApiError(error, "INVALID_INPUT", "TOO_MANY_SENDERS")) return { ok: false, message: error.message }
    throw error
  }
}

/** 보내는 주소를 지운다. 주소는 경로가 아니라 ?email=로 보낸다(메일 설계 5절). */
export async function removeSender(email: string): Promise<void> {
  if (mockServer) return db.removeSender(email)
  await request("DELETE", `/api/me/mail-settings/senders?email=${encodeURIComponent(email)}`)
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

/** 기본 프로필 저장 결과. 서버가 막은 값(400 INVALID_INPUT)이면 그 글이다. 화면이 먼저 같은 규칙을 보므로 드물다. */
export type SaveProfileResult = { ok: true; profile: Profile } | { ok: false; message: string }

/** 기본 프로필 전체를 저장한다(PUT /api/me/profile). 서버는 자격증 이름을 대표 표기로 바꾸고 연차를 다시 센 프로필을 돌려준다. */
export async function saveProfile(input: ProfileInput): Promise<SaveProfileResult> {
  if (mockServer) return { ok: true, profile: profileDb.saveProfile(input) }
  try {
    return { ok: true, profile: await request<Profile>("PUT", "/api/me/profile", input) }
  } catch (error) {
    if (isApiError(error, "INVALID_INPUT")) return { ok: false, message: error.message }
    throw error
  }
}

/** 성적증명서 읽기 결과. 읽지 못했으면 올리기 칸 아래에 보일 글이다. */
export type ReadTranscriptResult = { ok: true; transcript: Transcript } | { ok: false; message: string }

// 오류마다 보일 글(학점 계산기 문서 4절). 오류 이름은 초안이라 서버의 글 대신 화면이 정한 글을 보인다
const TRANSCRIPT_ERRORS: Record<string, string> = {
  TRANSCRIPT_TOO_LARGE: "파일이 너무 큽니다. PDF는 10MB, 사진은 5MB, 스캔한 PDF는 20쪽까지 읽을 수 있습니다.",
  TRANSCRIPT_UNSUPPORTED_TYPE: "PDF와 사진(JPG, PNG, GIF, WebP)만 읽을 수 있습니다.",
  TRANSCRIPT_UNREADABLE: "파일을 읽지 못했습니다. 손상되었거나 암호가 걸려 있지 않은지 확인해 주세요. 사진이면 종이가 반듯하게 보이도록 다시 찍어 주세요.",
  TRANSCRIPT_NO_COURSES: "과목을 찾지 못했습니다. 성적증명서가 맞는지 확인해 주세요.",
  LLM_LIMIT_REACHED: "이번 달 AI 사용 한도에 닿았습니다. 다음 달에 다시 읽을 수 있습니다.",
  LLM_UNAVAILABLE: "지금은 AI를 부를 수 없습니다. 잠시 뒤 다시 해 주세요.",
}

/** 성적증명서 한 파일을 읽는다(POST /api/transcripts/read, multipart의 file). 서버는 파일과 결과를 저장하지 않는다. */
export async function readTranscript(file: File): Promise<ReadTranscriptResult> {
  if (mockServer) return { ok: true, transcript: await transcriptDb.readTranscript(exampleTranscript()) }
  const body = new FormData()
  body.append("file", file)
  try {
    return { ok: true, transcript: await request<Transcript>("POST", "/api/transcripts/read", body) }
  } catch (error) {
    // 서버 앞(Caddy)이 본문 없이 413으로 끊을 수도 있다(shared/lib/api.ts의 tooLargeMessage)
    if (error instanceof ApiError && error.status === 413) return { ok: false, message: TRANSCRIPT_ERRORS.TRANSCRIPT_TOO_LARGE }
    if (isApiError(error, ...Object.keys(TRANSCRIPT_ERRORS))) return { ok: false, message: TRANSCRIPT_ERRORS[error.code] }
    throw error
  }
}
