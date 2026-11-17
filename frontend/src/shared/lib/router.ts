import { useEffect, useState } from "react"
import { useLocation, useNavigationType, type ShouldRevalidateFunctionArgs } from "react-router"

/**
 * 라우트의 shouldRevalidate를 만든다. keys는 이 라우트의 loader가 쓰는 주소 값(params)이다.
 * 이 값이 그대로면 ? 뒤(검색어, 필터)나 아래 화면이 바뀌어도 다시 읽지 않는다.
 * 저장 뒤 revalidate()나 같은 주소로 다시 들어올 때는 라우터 기본 판단을 따른다.
 *
 * 라우터 기본값은 ? 뒤가 바뀌면 모든 loader를 다시 부른다. 목록 필터처럼 화면에서만 쓰는 값은 loader와 무관하다.
 * loader가 ? 뒤를 쓰게 되면 그 라우트에는 이 함수를 쓰지 않는다.
 */
export function revalidateOnParams(...keys: string[]) {
  return ({ currentUrl, nextUrl, currentParams, nextParams, formMethod, defaultShouldRevalidate }: ShouldRevalidateFunctionArgs) => {
    if (formMethod || currentUrl.href === nextUrl.href) return defaultShouldRevalidate
    return keys.some((key) => currentParams[key] !== nextParams[key])
  }
}

/**
 * 주소(?q=)를 따르는 검색 칸의 글자. 칸은 자기 글자를 들고 있어야 한다: 주소 값만 따르면 라우터가 값을 늦게 돌려줘
 * 한글 조합이 깨지고 커서가 끝으로 튄다. 칸에 쳐서 생긴 이동(REPLACE)이 아닌 이동(그룹 바꾸기, 메뉴, 뒤로 가기)이 오면
 * 칸을 주소 값으로 다시 맞춘다. 칸을 고칠 때는 돌려준 setText와 주소 고치기를 함께 부른다.
 */
export function useSearchText(urlText: string): [string, (text: string) => void] {
  const location = useLocation()
  const navigationType = useNavigationType()
  const [text, setText] = useState(urlText)
  const [seenKey, setSeenKey] = useState(location.key)
  if (seenKey !== location.key) {
    setSeenKey(location.key)
    if (navigationType !== "REPLACE") setText(urlText)
  }
  return [text, setText]
}

/**
 * 앞 화면이 navigate의 state로 넘긴 값(도착 알림 등)을 처음 한 번만 읽는다. 기록의 state는 바로 지운다:
 * 브라우저가 새로 고침과 뒤로 가기에도 state를 남겨 같은 알림이 다시 뜨기 때문이다.
 */
export function useArrivalState<T>(): T | null {
  const location = useLocation()
  const [state] = useState(() => (location.state as T | null) ?? null)
  useEffect(() => {
    if (state) clearRouterState()
  }, [state])
  return state
}

/**
 * navigate로 넘긴 state를 브라우저 기록에서 지운다. 새로 고침이나 뒤로 가기에서 같은 알림·창이 다시 뜨지 않게 한다.
 * 같은 주소로 navigate(…, { state: null })를 부르면 화면의 loader가 한 번 더 돌아서, 라우터가 기록에 둔 칸(usr)만 비운다.
 */
export function clearRouterState() {
  window.history.replaceState({ ...window.history.state, usr: null }, "")
}
