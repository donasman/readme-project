// 서버를 부르는 곳. 각 층의 api.ts만 쓴다. 규칙은 docs/리드미_백엔드_구조.md 5절(쿠키, CSRF, 오류 꼴)이다.
// loader에서 난 오류는 오류 화면(app/route-error.tsx)이, 버튼에서 나서 화면이 다루지 않은 오류는 아래 알림(app/action-error.tsx)이 보인다.
import { sentence } from "./korean"

/**
 * 가짜 서버(mocks/)를 부르는지. 각 api.ts가 이 값으로 고른다.
 * npm run dev(development 모드)만 가짜 서버다. 화면 회귀 검사가 가짜 서버의 예시 데이터로 찍는다.
 * npm run dev:server(server 모드)와 빌드는 진짜 서버를 부른다.
 */
export const mockServer = import.meta.env.MODE === "development"

/** 서버 주소. 화면과 서버는 주소가 달라(app.도메인, api.도메인) 모든 요청이 이 앞에 붙는다. 운영 빌드는 VITE_API_ORIGIN으로 받는다. */
export const SERVER_ORIGIN: string = import.meta.env.VITE_API_ORIGIN ?? "http://localhost:8080"

/** 서버가 돌려준 오류({ code, message }). 오류에 함께 실린 값(postId 등)은 body에 그대로 있다. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly body: Record<string, unknown>

  constructor(status: number, body: Record<string, unknown>) {
    super(typeof body.message === "string" ? body.message : `서버가 ${status}로 답했습니다`)
    this.name = "ApiError"
    this.status = status
    this.code = typeof body.code === "string" ? body.code : ""
    this.body = body
  }
}

/** 서버에 닿지 못했다(끊긴 연결, 꺼진 서버). 응답이 없어 상태는 0이다. 가짜 서버도 ?mock=offline에서 이것을 던진다. */
export const networkError = () => new ApiError(0, { message: "서버에 연결하지 못했습니다. 인터넷 연결을 확인하고 다시 해 주세요." })

/** 오류를 사람에게 보일 글: 서버가 이유를 준 오류(4xx)는 그 글, 끊긴 연결은 연결 안내, 그 밖(서버 고장, 화면의 버그)은 다시 해 보라는 글. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError && (error.status === 0 || (error.status < 500 && error.code))) return sentence(error.message)
  if (error instanceof ApiError) return "서버에 문제가 생겼습니다. 잠시 뒤 다시 해 주세요."
  return "문제가 생겼습니다. 다시 해 주세요."
}

/** 파일이 서버가 받는 크기(20MB)를 넘을 때의 글. 화면은 보내기 전에도 이 글로 막는다. */
export const FILE_TOO_LARGE = "파일이 너무 큽니다. 20MB까지 올릴 수 있습니다."

/**
 * 413이면 알릴 글, 아니면 null. 서버 앞(Caddy)이 본문 없이 끊을 수도 있어 code와 관계없이 "너무 크다"로 본다
 * (경험카드 문서 6절 7번, 공고 문서 8절 29번). 서버가 이유를 주었으면 그 글이다.
 */
export function tooLargeMessage(error: unknown): string | null {
  if (!(error instanceof ApiError) || error.status !== 413) return null
  return error.code ? sentence(error.message) : FILE_TOO_LARGE
}

type Method = "GET" | "POST" | "PUT" | "DELETE"

/** API 주소를 만든다: apiPath`/api/groups/${groupId}/me`. 끼워 넣는 값(주소에서 온 id, 메일의 토큰)은 인코딩한다. */
export function apiPath(parts: TemplateStringsArray, ...values: string[]): string {
  return parts.reduce((path, part, i) => `${path}${encodeURIComponent(values[i - 1])}${part}`)
}

/** 바꾸는 요청에 실을 CSRF 값. 서버가 XSRF-TOKEN 쿠키로 내려 준다. */
/**
 * 서버의 id는 숫자이고 화면의 id는 문자열이다(구조 문서 5.1). 응답을 읽을 때 이름이 id이거나 Id로 끝나는 숫자 칸(groupId, postId, leaderId …)과
 * Ids로 끝나는 숫자 목록(cardIds)을 문자열로 바꿔, 각 api.ts가 따로 바꾸지 않게 한다.
 */
const stringIds = (key: string, value: unknown) => {
  if (typeof value === "number" && (key === "id" || key.endsWith("Id"))) return String(value)
  if (Array.isArray(value) && key.endsWith("Ids")) return value.map((v) => (typeof v === "number" ? String(v) : v))
  return value
}

/** 응답 본문을 읽는다. 비었으면(204) undefined. */
async function readBody(response: Response): Promise<unknown> {
  const text = await response.text()
  return text ? JSON.parse(text, stringIds) : undefined
}

const csrfToken = () =>
  document.cookie
    .split("; ")
    .find((c) => c.startsWith("XSRF-TOKEN="))
    ?.slice("XSRF-TOKEN=".length)

async function send(method: Method, path: string, body: unknown): Promise<Response | ApiError> {
  const headers: Record<string, string> = {}
  // 파일(FormData)은 브라우저가 multipart 머리를 붙이므로 그대로 보낸다. 나머지는 JSON이다
  const json = body !== undefined && !(body instanceof FormData)
  if (json) headers["Content-Type"] = "application/json"
  const token = method === "GET" ? undefined : csrfToken()
  if (token) headers["X-XSRF-TOKEN"] = token
  const response = await fetch(SERVER_ORIGIN + path, {
    method,
    headers,
    credentials: "include",
    body: json ? JSON.stringify(body) : (body as FormData | undefined),
  }).catch(() => {
    // fetch는 연결이 안 되면 TypeError를 던진다. 화면의 버그(TypeError)와 나누려고 바꾼다
    throw networkError()
  })
  if (response.ok) return response
  const error = await readBody(response).catch(() => undefined)
  return new ApiError(response.status, error && typeof error === "object" ? (error as Record<string, unknown>) : {})
}

/**
 * 서버를 부른다. 204면 undefined, 실패하면 ApiError를 던진다.
 * 첫 바꾸는 요청에는 CSRF 쿠키가 아직 없을 수 있다. 서버는 막은 응답에 새 쿠키를 실어 주므로 그때 한 번만 다시 보낸다.
 */
export async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  let result = await send(method, path, body)
  if (result instanceof ApiError && result.code === "CSRF_INVALID") result = await send(method, path, body)
  if (result instanceof ApiError) throw result
  return (await readBody(result)) as T
}

/** 서버가 이 오류 이름 가운데 하나로 답했는지. 화면이 칸 아래에 알릴 오류를 고를 때 쓴다. */
export const isApiError = (error: unknown, ...codes: string[]): error is ApiError =>
  error instanceof ApiError && codes.includes(error.code)
