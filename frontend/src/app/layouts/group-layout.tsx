import { Outlet } from "react-router"

import { AppHeader } from "./app-header"
import { GroupBand } from "./group-band"

/** 그룹 화면들이 함께 쓰는 틀: 위쪽 헤더, 그룹 머리 영역(이름, 멤버, 탭), 그 아래 화면. 글자 규칙은 뿌리 틀(root-layout.tsx)이 건다. */
export function GroupLayout() {
  return (
    <>
      <AppHeader />
      <GroupBand />
      <Outlet />
    </>
  )
}
