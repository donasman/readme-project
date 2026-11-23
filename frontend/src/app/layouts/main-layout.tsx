import { Outlet } from "react-router"

import { AppHeader } from "./app-header"

/** 그룹 밖 화면들(홈, 내 그룹, 경험카드, 설정 …)이 함께 쓰는 틀: 위쪽 헤더와 그 아래 화면. 글자 규칙은 뿌리 틀이 건다. */
export function MainLayout() {
  return (
    <>
      {/* 그룹 밖 화면은 모두 Page narrow다 */}
      <AppHeader narrow />
      <Outlet />
    </>
  )
}
