import type { ReactNode } from "react"
import { Outlet, ScrollRestoration } from "react-router"

/**
 * 모든 화면의 뿌리 틀. 디자인 화면의 뿌리와 같은 글자 규칙을 건다: 본문 줄 간격과 자간, 한글 낱말 단위 줄바꿈,
 * 끝줄 고르기. @container는 화면 제목 크기(cqw)의 기준이다.
 */
export function RootFrame({ children }: { children?: ReactNode }) {
  return (
    <div className="@container min-h-svh bg-background text-base leading-body tracking-body break-keep text-pretty text-foreground">
      {children}
    </div>
  )
}

/** 뿌리 라우트의 화면: 틀 안에 아래 화면을 그리고, 이동할 때 스크롤 위치를 되돌린다. */
export function RootLayout() {
  return (
    <>
      <RootFrame>
        <Outlet />
      </RootFrame>
      <ScrollRestoration />
    </>
  )
}
