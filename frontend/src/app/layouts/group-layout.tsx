import { Outlet } from "react-router"

import { GroupHeader } from "./group-header"

/** 그룹 화면들이 함께 쓰는 틀: 머리와 그 아래 화면. 글자 규칙은 뿌리 틀(root-layout.tsx)이 건다. */
export function GroupLayout() {
  return (
    <>
      <GroupHeader />
      <Outlet />
    </>
  )
}
