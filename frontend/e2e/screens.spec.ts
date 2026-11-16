import { expect, test } from "@playwright/test"

// 화면 점검: 주요 화면이 콘솔 오류 없이 열리고 옆으로 넘치지 않는다. 픽셀 비교는 npm run regress가 한다.
const SCREENS = ["/welcome", "/", "/groups/be/posts", "/groups/be/posts/be5", "/my-posts", "/cards", "/settings/account"]

for (const path of SCREENS) {
  test(`${path} 화면`, async ({ page }) => {
    const errors: string[] = []
    page.on("console", (message) => {
      if (message.type() === "error") errors.push(message.text())
    })
    page.on("pageerror", (error) => errors.push(error.message))

    await page.goto(path)
    await expect(page.getByRole("heading").first()).toBeVisible()

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow, "옆으로 넘친 폭(px)").toBeLessThanOrEqual(0)
    expect(errors).toEqual([])
  })
}
