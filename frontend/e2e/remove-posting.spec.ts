import { expect, test } from "@playwright/test"

// 내 공고에서 빼기(12차 시안 안 1): 상세 옆 칸에서 창을 열어 지워질 것을 보고 빼면, 목록으로 가서 알린다. 그룹의 관심도 꺼진다.
test("지원한 공고를 빼면 지워질 것을 알리고 목록에서 사라진다", async ({ page }) => {
  await page.goto("/my-posts/p-pub1")
  await page.getByRole("button", { name: "내 공고에서 빼기" }).click()
  const dialog = page.getByRole("dialog", { name: "내 공고에서 뺄까요?" })
  await expect(dialog.getByText("지원함 · 필기 단계")).toBeVisible()
  await expect(dialog.getByText("나만 보는 메모")).toBeVisible()
  await expect(dialog.getByText("전형 일정 1건")).toBeVisible()
  await expect(dialog.getByText("공기업 전산직 그룹에서 켠 관심")).toBeVisible()

  await dialog.getByRole("button", { name: "빼기" }).click()
  await expect(page).toHaveURL(/\/my-posts$/)
  await expect(page.getByRole("status").filter({ hasText: "예시 공사 · 전산직 7급 공채를 내 공고에서 뺐습니다" })).toBeVisible()
  await expect(page.getByRole("link", { name: /전산직 7급 공채/ })).toHaveCount(0)
})
