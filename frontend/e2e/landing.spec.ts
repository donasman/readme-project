import { expect, test } from "@playwright/test"

// 랜딩은 로그인했으면 로그인·회원가입 대신 대시보드로 가는 버튼을 보인다(2026-11-23). 설정의 "리드미 소개"로 들어온다.
test("로그인한 사람은 랜딩에서 대시보드로 간다", async ({ page }) => {
  await page.goto("/settings/account")
  await page.getByRole("link", { name: "리드미 소개" }).click()
  await expect(page.getByRole("link", { name: "회원가입" })).toHaveCount(0)
  await page.getByRole("link", { name: "대시보드로 가기" }).first().click()
  await expect(page.getByRole("heading", { name: "다가오는 마감" })).toBeVisible()
})

test("로그인 전에는 랜딩에 로그인·회원가입이 보인다", async ({ page }) => {
  await page.goto("/welcome?mock=guest")
  await expect(page.getByRole("link", { name: "회원가입" }).first()).toBeVisible()
  await expect(page.getByRole("link", { name: "대시보드로 가기" })).toHaveCount(0)
})
