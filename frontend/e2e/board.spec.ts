import { expect, test } from "@playwright/test"

// 지원 현황의 "바꾸기"는 옆 창(상태 바꾸기)에 띄울 공고를 고르는 하나뿐인 길이다. 표가 칸보다 넓어도 가려지면 안 된다.
test("지원 현황의 바꾸기는 표를 밀지 않아도 보이고, 누르면 그 공고를 고친다", async ({ page }, testInfo) => {
  // 데스크톱은 본문 칸이 표보다 좁아지는 1024 폭에서 본다
  if (testInfo.project.name === "desktop") await page.setViewportSize({ width: 1024, height: 800 })
  await page.goto("/board")

  const change = page.getByRole("button", { name: "예시 공사 전산직 7급 공채 상태 바꾸기" })
  const inside = await change.evaluate((el) => {
    let box = el.parentElement
    while (box && getComputedStyle(box).overflowX === "visible") box = box.parentElement
    const b = el.getBoundingClientRect()
    const r = box!.getBoundingClientRect()
    return b.left >= r.left && b.right <= r.right
  })
  expect(inside, "바꾸기가 표 상자 안에 보인다").toBe(true)

  await change.click()
  await expect(page.getByText("예시 공사 · 전산직 7급 공채")).toBeVisible()
})
