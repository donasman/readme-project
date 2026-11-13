import type { ShouldRevalidateFunctionArgs } from "react-router"

/**
 * 주소의 ? 뒤(검색어, 필터)만 바뀌면 loader를 다시 부르지 않는다. 라우트의 shouldRevalidate에 그대로 넣는다.
 * 그 밖의 경우(경로가 바뀜, 저장 뒤 revalidate 등)는 라우터 기본 판단을 따른다.
 */
export function ignoreSearchChanges({ currentUrl, nextUrl, defaultShouldRevalidate }: ShouldRevalidateFunctionArgs) {
  if (currentUrl.pathname === nextUrl.pathname && currentUrl.search !== nextUrl.search) return false
  return defaultShouldRevalidate
}
