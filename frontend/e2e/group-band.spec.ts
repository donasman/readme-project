import { expect, test } from "@playwright/test"

// 그룹 머리 영역(11차 시안): 헤더가 지금 그룹을 보이고, 머리 영역의 탭이 지금 화면을 보인다.
// 초대 링크는 만든 뒤 멤버 화면으로 들고 간다. 멤버 화면에서 또 만들어도 새 링크가 보여야 한다.
test("그룹 머리 영역에서 초대 링크를 만들면 멤버 화면에 그 링크가 보인다", async ({ page }) => {
  await page.goto("/groups/be/posts")
  await expect(page.getByRole("button", { name: "내 그룹: 백엔드 스터디" })).toBeVisible()
  const band = page.getByRole("region", { name: "백엔드 스터디 그룹" })
  await expect(band.getByRole("link", { name: /^공고/ })).toHaveAttribute("aria-current", "page")

  await band.getByRole("button", { name: "초대 링크 만들기" }).click()
  await expect(page).toHaveURL(/\/groups\/be\/members$/)
  await expect(band.getByRole("link", { name: /^멤버/ })).toHaveAttribute("aria-current", "page")
  const link = page.locator("#members-link")
  await expect(link).toHaveValue(/\/invite\//)
  const first = await link.inputValue()

  await band.getByRole("button", { name: "초대 링크 만들기" }).click()
  await expect(link).toHaveValue(/\/invite\//)
  await expect(link).not.toHaveValue(first)
})

test("그룹 밖에서는 내 그룹이 내 그룹 화면으로 가는 링크다", async ({ page }, testInfo) => {
  await page.goto("/cards")
  await page.getByRole("link", { name: "내 그룹", exact: true }).click()
  await expect(page).toHaveURL(/\/groups$/)
  await expect(page.getByRole("link", { name: "내 그룹", exact: true })).toHaveAttribute("aria-current", "page")
  // 휴대폰에서는 나머지 메뉴가 메뉴(☰) 안에 있다
  if (testInfo.project.name === "mobile") {
    await page.getByRole("button", { name: "메뉴" }).click()
    await expect(page.getByRole("menuitem", { name: "경험카드" })).toBeVisible()
  }
})
