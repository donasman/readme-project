// 소개 페이지의 앱 화면(public/welcome/)과 링크 미리보기 이미지(public/og.png)를 진짜 앱에서 찍는다.
// 앱 화면이 바뀌면 다시 돌린다. 가짜 서버 개발 서버(npm run dev)를 띄운 채로:
//   npm run shots            (주소는 SHOTS_URL, 기본 http://127.0.0.1:5173)
// 가짜 서버의 날짜는 고정이라 D-day가 매번 같다. 미리보기 이미지는 /dev/og(개발 서버에서만 열린다)를 찍고, 그 화면이 앞에서 찍은 앱 화면을 쓴다.
import { mkdirSync } from "node:fs"
import { dirname, join } from "node:path"

import { chromium } from "@playwright/test"

const APP = process.env.SHOTS_URL ?? "http://127.0.0.1:5173"
const PUBLIC = join(import.meta.dirname, "..", "public")
const LIST = "/groups/be/posts"

const SHOTS = [
  { file: "welcome/app-light.jpg", path: LIST, width: 1280, height: 800, theme: "light" },
  { file: "welcome/app-dark.jpg", path: LIST, width: 1280, height: 800, theme: "dark" },
  { file: "welcome/app-mobile-light.jpg", path: LIST, width: 390, height: 680, theme: "light" },
  { file: "welcome/app-mobile-dark.jpg", path: LIST, width: 390, height: 680, theme: "dark" },
  { file: "og.png", path: "/dev/og", width: 1200, height: 630, theme: "light", scale: 1 },
]

const browser = await chromium.launch()
try {
  for (const shot of SHOTS) {
    const page = await browser.newPage({ viewport: { width: shot.width, height: shot.height }, deviceScaleFactor: shot.scale ?? 2 })
    await page.addInitScript((theme) => localStorage.setItem("theme", theme), shot.theme)
    await page.goto(APP + shot.path, { waitUntil: "networkidle" })
    await page.evaluate(() => document.fonts.ready)
    // 그림이 다 그려진 뒤에 찍는다(미리보기 이미지 안의 앱 화면)
    await page.waitForFunction(() => [...document.images].every((img) => img.complete))
    const out = join(PUBLIC, shot.file)
    mkdirSync(dirname(out), { recursive: true })
    await page.screenshot({ path: out, ...(out.endsWith(".jpg") ? { type: "jpeg", quality: 82 } : {}) })
    console.log(shot.file)
    await page.close()
  }
} finally {
  await browser.close()
}
