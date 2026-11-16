import { expect, test } from "@playwright/test"

// 화면 점검: 주요 화면이 콘솔 오류 없이 열리고 옆으로 넘치지 않는다. 픽셀 비교는 npm run regress가 한다.
// 제목은 그 화면에만 있는 것으로 고른다. 오류 화면에도 제목이 있어서, 아무 제목이나 보면 깨진 화면도 통과한다.
const SCREENS = [
  ["/welcome", "리드미에서 하는 일"],
  ["/", "다가오는 마감"],
  ["/groups/be/posts", "공고"],
  ["/groups/be/posts/be5", "공고 내용"],
  ["/my-posts", "내 공고"],
  ["/cards", "경험카드"],
  ["/settings/account", "로그인 방법"],
]

for (const [path, heading] of SCREENS) {
  test(`${path} 화면`, async ({ page }) => {
    const errors: string[] = []
    page.on("console", (message) => {
      if (message.type() === "error") errors.push(message.text())
    })
    page.on("pageerror", (error) => errors.push(error.message))

    await page.goto(path)
    await expect(page.getByRole("heading", { name: heading, exact: true })).toBeVisible()

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow, "옆으로 넘친 폭(px)").toBeLessThanOrEqual(0)
    expect(errors).toEqual([])
  })
}
