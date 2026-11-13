import type { ShouldRevalidateFunctionArgs } from "react-router"

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
