// 서버를 부르는 곳. 각 층의 api.ts만 쓴다. 규칙은 docs/리드미_백엔드_구조.md 5절(쿠키, CSRF, 오류 꼴)이다.
// shortcut: 서버 오류(500)와 끊긴 연결은 화면에 알리지 않는다(loader에서 나면 오류 화면, 버튼에서 나면 그대로 멈춘다), 알릴 자리를 시안에 그리면 그때 한 곳에서 잡는다

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

type Method = "GET" | "POST" | "PUT" | "DELETE"

/** API 주소를 만든다: apiPath`/api/groups/${groupId}/me`. 끼워 넣는 값(주소에서 온 id, 메일의 토큰)은 인코딩한다. */
export function apiPath(parts: TemplateStringsArray, ...values: string[]): string {
  return parts.reduce((path, part, i) => `${path}${encodeURIComponent(values[i - 1])}${part}`)
}

/** 바꾸는 요청에 실을 CSRF 값. 서버가 XSRF-TOKEN 쿠키로 내려 준다. */
const csrfToken = () =>
  document.cookie
    .split("; ")
    .find((c) => c.startsWith("XSRF-TOKEN="))
    ?.slice("XSRF-TOKEN=".length)

async function send(method: Method, path: string, body: unknown): Promise<Response | ApiError> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers["Content-Type"] = "application/json"
  const token = method === "GET" ? undefined : csrfToken()
  if (token) headers["X-XSRF-TOKEN"] = token
  const response = await fetch(SERVER_ORIGIN + path, {
    method,
    headers,
    credentials: "include",
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (response.ok) return response
  return new ApiError(response.status, await response.json().catch(() => ({})))
}

/**
 * 서버를 부른다. 204면 undefined, 실패하면 ApiError를 던진다.
 * 첫 바꾸는 요청에는 CSRF 쿠키가 아직 없을 수 있다. 서버는 막은 응답에 새 쿠키를 실어 주므로 그때 한 번만 다시 보낸다.
 */
export async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  let result = await send(method, path, body)
  if (result instanceof ApiError && result.code === "CSRF_INVALID") result = await send(method, path, body)
  if (result instanceof ApiError) throw result
  return (result.status === 204 ? undefined : await result.json()) as T
}

/** 서버가 이 오류 이름 가운데 하나로 답했는지. 화면이 칸 아래에 알릴 오류를 고를 때 쓴다. */
export const isApiError = (error: unknown, ...codes: string[]): error is ApiError =>
  error instanceof ApiError && codes.includes(error.code)
