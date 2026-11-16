import { expect, test } from "@playwright/test"

// 메일 확인함(메일 설계 4.4·4.5): 다음 전형 일정이 있는 메일을 반영하면 지원 상태가 바뀌고 달력의 전형 일정에 들어간다.
test("일정이 있는 메일을 반영하면 달력의 전형 일정에 들어간다", async ({ page }) => {
  await page.goto("/mail")
  await expect(page.getByText("다음 전형: 2차 면접")).toBeVisible()
  await page
    .getByRole("listitem")
    .filter({ hasText: "[예시 보안] 2차 면접 일정 안내" })
    .getByRole("button", { name: "반영" })
    .click()
  await expect(page.getByRole("status").filter({ hasText: "예시 보안 메일을 반영했습니다." })).toHaveCount(1)

  await page.getByRole("navigation", { name: "내 공간" }).getByRole("link", { name: "달력" }).click()
  const steps = page.getByRole("region", { name: "11월의 전형 일정" })
  await expect(steps.getByText("주니어 서버 개발")).toBeVisible()
  await expect(steps.getByText("2차 면접")).toBeVisible()
})
