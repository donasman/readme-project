import { expect, test } from "@playwright/test"

// 자료로 카드 만들기(경험카드 문서 4.6): 글을 읽고 뽑기를 시작하면 조각마다 진행을 보이고, 끝나면 만든 카드 수를 알린다.
// 가짜 서버는 조각마다 1.2초가 걸린다.
test("붙여넣은 글로 카드를 뽑으면 진행을 보이고 끝을 알린다", async ({ page }) => {
  await page.goto("/cards/sources")
  await page.getByLabel("글 붙여넣기").fill("2025년 3월부터 5월까지 예시 커머스에서 주문 조회 API 응답 시간을 개선했다.")
  await page.getByRole("button", { name: "이 글 읽기" }).click()
  await expect(page.getByRole("heading", { name: "자료를 읽었습니다" })).toBeVisible()

  await page.getByRole("button", { name: "카드 뽑기 시작" }).click()
  await expect(page.getByRole("heading", { name: "카드를 뽑는 중입니다" })).toBeVisible()
  await expect(page.getByRole("heading", { name: /카드 \d+장을 만들었습니다/ })).toBeVisible({ timeout: 10_000 })
  await expect(page.getByRole("link", { name: "카드 확인하기" })).toBeVisible()
})

// 뽑기는 프로필의 빈 칸도 채운다(12차 시안). 결과 화면이 뽑기 전과 달라진 칸에 "이번에 채움"을 붙이고, 프로필 확인으로 이어진다.
test("뽑기가 채운 프로필 칸을 표시하고 프로필 확인으로 보낸다", async ({ page }) => {
  await page.goto("/cards/sources?mock=empty-profile")
  await page.getByLabel("글 붙여넣기").fill("2025년 3월부터 5월까지 예시 커머스에서 주문 조회 API 응답 시간을 개선했다.")
  await page.getByRole("button", { name: "이 글 읽기" }).click()
  await page.getByRole("button", { name: "카드 뽑기 시작" }).click()
  const check = page.getByRole("region", { name: "프로필 칸 확인" })
  await expect(check).toBeVisible({ timeout: 10_000 })
  await expect(check.getByText("이번에 채움")).toHaveCount(4)
  await expect(check.getByText("비어 있음")).toHaveCount(2)

  await check.getByRole("link", { name: "프로필 확인하기" }).click()
  await expect(page.getByRole("status").filter({ hasText: "자료에서 읽은 값을 넣었습니다" })).toBeVisible()
  await expect(page.locator("#pf-name")).toHaveValue("김다온")
})
