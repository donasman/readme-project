// 로그인한 뒤 돌아갈 화면. 로그인 전 화면(초대 수락)이 로그인·가입으로 보내기 전에 두고, 로그인한 화면들의 loader(sessionLoader)가 꺼낸다.
// 인증 메일의 링크는 새 탭으로 열리고 구글 로그인은 서버가 늘 홈으로 돌려보내므로, 주소나 이동 상태가 아니라 localStorage에 둔다(로그인 문서 4.1).
const KEY = "readme:return-to"

/** 이 앱 안의 주소인지: /로 시작하고 다른 사이트(//host, /\host)가 아니다. */
export const isAppPath = (path: string) => path.startsWith("/") && !path.startsWith("//") && !path.startsWith("/\\")

/** 돌아갈 화면을 둔다. 저장을 막은 브라우저에서는 두지 못하고, 로그인한 뒤 홈으로 간다. */
export function rememberReturnTo(path: string): void {
  try {
    localStorage.setItem(KEY, path)
  } catch {
    // 저장을 막은 브라우저: 돌아오지 않는다
  }
}

/** 둔 화면을 꺼내고 지운다. 없거나 이 앱의 주소가 아니면 null. */
export function takeReturnTo(): string | null {
  try {
    const path = localStorage.getItem(KEY)
    localStorage.removeItem(KEY)
    return path && isAppPath(path) ? path : null
  } catch {
    return null
  }
}
