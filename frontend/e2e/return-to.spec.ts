import { expect, test } from "@playwright/test"

// 로그인이 풀린 채 공유받은 링크를 열면 로그인 화면으로 가고, 로그인한 뒤에는 홈이 아니라 그 화면으로 돌아간다(로그인 문서 4.1).
test("로그인한 뒤 열려던 그룹 공고로 돌아간다", async ({ page }) => {
  await page.goto("/groups/be/posts/be1?mock=guest")
  await expect(page).toHaveURL(/\/login$/)
  await page.getByLabel("이메일").fill("daon@example.com")
  await page.getByLabel("비밀번호").fill("readme1234")
  await page.getByRole("button", { name: "로그인", exact: true }).click()
  await expect(page).toHaveURL(/\/groups\/be\/posts\/be1/)
  await expect(page.getByRole("heading", { level: 1, name: "백엔드 개발자 신입" })).toBeVisible()
})
