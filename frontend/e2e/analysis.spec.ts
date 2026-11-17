import { expect, test } from "@playwright/test"

// 공고 분석(12차 시안 안 A): 공고 내용은 요약이 먼저고 원문으로 바꿔 본다. 관심을 켠 그룹 공고는 맞는 카드가 있는 내 공고로 이어진다.
test("요약과 원문을 바꿔 보고, 그룹 공고에서 내 공고의 맞는 카드로 간다", async ({ page }) => {
  await page.goto("/groups/be/posts/be1")
  const content = page.getByRole("region", { name: "공고 내용" })
  await expect(content.getByRole("heading", { name: "필수 역량" })).toBeVisible()
  await expect(content.getByText("자기소개서 문항")).toBeVisible()

  await content.getByRole("button", { name: "원문" }).click()
  await expect(content.getByRole("heading", { name: "필수 요건" })).toBeVisible()
  await expect(content.getByText("자기소개서 문항")).toHaveCount(0)

  await content.getByRole("link", { name: "맞는 카드는 내 공고에서 보기" }).click()
  await expect(page.getByRole("heading", { level: 1, name: "백엔드 개발자 신입" })).toBeVisible()
  await expect(page.getByRole("region", { name: "맞는 카드" })).toBeVisible()
})
