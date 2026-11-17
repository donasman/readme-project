import { expect, test } from "@playwright/test"

// 회원가입은 이름, 이메일, 비밀번호만 받는다(2026-11-17, 로그인 문서 4.1). 생년월일은 기본 프로필에서 선택으로 넣는다.
test("이름, 이메일, 비밀번호로 가입하면 확인 메일 화면으로 간다", async ({ page }) => {
  await page.goto("/signup")
  await expect(page.getByLabel(/생년월일/)).toHaveCount(0)
  await expect(page.getByRole("checkbox")).toHaveCount(0)
  await page.getByLabel("표시 이름").fill("다온")
  await page.getByLabel("이메일").fill("new-person@example.com")
  await page.getByLabel("비밀번호").fill("readme1234")
  await page.getByRole("button", { name: "가입하기" }).click()
  await expect(page).toHaveURL(/\/signup\/sent$/)
})
