import { expect, test } from "@playwright/test"

// 공고 분석(12차 시안): 그룹 공고 상세는 안 A(요약이 먼저고 원문으로 바꿔 본다), 내 공고 상세는 안 B(요건과 내 카드). 관심을 켠 그룹 공고는 내 공고로 이어진다.
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
  // 내 공고 상세는 안 B: 요건마다 카드 있음, 프로필에 있음, 카드 없음, 판단 못 함
  const requirements = page.getByRole("region", { name: "요건과 내 카드" })
  await expect(requirements.getByText(/^카드 \d+장$/).first()).toBeVisible()
  await expect(requirements.getByRole("heading", { name: "맞는 카드" })).toBeVisible()
})

test("내 공고 상세는 프로필에 있는 자격증 요건을 프로필에 있음으로 보인다", async ({ page }) => {
  await page.goto("/my-posts/p-pub1")
  const requirements = page.getByRole("region", { name: "요건과 내 카드" })
  const row = requirements.getByRole("listitem").filter({ hasText: "정보처리기사 또는 같은 수준의 자격" })
  await expect(row.getByText("프로필에 있음")).toBeVisible()
  await expect(row.getByText("프로필: 정보처리기사")).toBeVisible()
  await expect(page.getByRole("region", { name: "자기소개서 문항" })).toBeVisible()
})
