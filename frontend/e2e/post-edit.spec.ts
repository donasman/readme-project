import { expect, test } from "@playwright/test"

// 가짜 서버에서 be5는 마감을 모르는 내 공고, mes1은 남과 함께 쓰는 공고다(mocks/db.ts).

test("내 공고를 고치면 상세에 새 값이 보인다", async ({ page }) => {
  await page.goto("/groups/be/posts/be5")
  await page.getByRole("button", { name: "공고 고치기" }).click()
  const dialog = page.getByRole("dialog")
  await dialog.getByLabel("공고명").fill("플랫폼 엔지니어")
  await dialog.getByRole("button", { name: "상시" }).click()
  await dialog.getByRole("button", { name: "저장" }).click()

  await expect(dialog).toBeHidden()
  await expect(page.getByRole("heading", { name: "플랫폼 엔지니어" })).toBeVisible()
})

test("빈 회사명은 칸 아래에 알린다", async ({ page }) => {
  await page.goto("/groups/be/posts/be5")
  await page.getByRole("button", { name: "공고 고치기" }).click()
  const dialog = page.getByRole("dialog")
  await dialog.getByLabel("회사명").fill("")
  await dialog.getByRole("button", { name: "저장" }).click()

  await expect(dialog.getByText("회사명을 넣어 주세요.")).toBeVisible()
})

test("남과 함께 쓰는 공고는 저장하면 막히고 닫기만 남는다", async ({ page }) => {
  await page.goto("/groups/mes/posts/mes1")
  await page.getByRole("button", { name: "공고 고치기" }).click()
  const dialog = page.getByRole("dialog")
  await dialog.getByRole("button", { name: "저장" }).click()

  await expect(dialog.getByText("고칠 수 없는 공고입니다")).toBeVisible()
  await expect(dialog.getByLabel("회사명")).toBeDisabled()
  await expect(dialog.getByRole("button", { name: "저장" })).toHaveCount(0)
  await expect(dialog.getByRole("button", { name: "닫기" })).toBeFocused()
})
