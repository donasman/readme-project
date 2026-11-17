import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { RouterProvider } from "react-router/dom"

import "./index.css"
import "@/app/arc-sound"
import { router } from "@/app/router"
import { ThemeProvider } from "@/shared/theme/theme-provider"

// 배포 뒤 오래 열어 둔 탭은 지난 배포의 화면 파일(이름에 해시가 붙는다)을 찾다 실패하고, 새로 고치기 전에는 오류 화면에 갇힌다.
// 그때 한 번 새로 고쳐 새 파일을 받는다. 10초 안에 또 실패하면(파일이 정말 없다) 새로 고치지 않고 오류 화면을 둔다
window.addEventListener("vite:preloadError", (event) => {
  try {
    if (Date.now() - Number(sessionStorage.getItem("readme:preload-reload") ?? 0) < 10_000) return
    sessionStorage.setItem("readme:preload-reload", String(Date.now()))
  } catch {
    return
  }
  event.preventDefault()
  location.reload()
})

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ThemeProvider>
      <RouterProvider router={router} />
    </ThemeProvider>
  </StrictMode>
)
