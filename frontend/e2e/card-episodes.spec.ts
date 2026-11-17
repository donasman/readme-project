import { expect, test } from "@playwright/test"

// 장면 단위 카드(경험카드 문서 4.8): 같은 경험의 카드는 목록에서 한 묶음이고, "장면 더하기"는 그 경험 이름을 채운 새 카드를 연다.
test("경험 묶음에서 장면을 더하면 같은 묶음에 들어간다", async ({ page }) => {
  await page.goto("/cards")
  const group = page.getByRole("region", { name: "주문 조회 API 개선 프로젝트" })
  await expect(group.getByText("장면 2장")).toBeVisible()

  await group.getByRole("link", { name: "장면 더하기" }).click()
  await expect(page.getByRole("heading", { name: "카드 만들기", level: 1 })).toBeVisible()
  await expect(page.getByLabel("경험 이름")).toHaveValue("주문 조회 API 개선 프로젝트")
  await page.getByLabel("제목").fill("첫 설계가 틀려 다시 나누기")
  await page.getByLabel("배운 점").fill("나누기 전에 읽는 쪽을 먼저 센다.")
  await page.getByRole("button", { name: "저장" }).click()

  // 상세는 경험 이름과 배운 점을 보이고, 비운 어려움은 보이지 않는다
  await expect(page.getByRole("heading", { name: "첫 설계가 틀려 다시 나누기", level: 1 })).toBeVisible()
  const story = page.getByRole("region", { name: "이야기" })
  await expect(story.getByText("나누기 전에 읽는 쪽을 먼저 센다.")).toBeVisible()
  await expect(story.getByRole("heading", { name: "어려움" })).toHaveCount(0)

  await page.getByRole("link", { name: "경험카드" }).first().click()
  await expect(page.getByRole("region", { name: "주문 조회 API 개선 프로젝트" }).getByText("장면 3장")).toBeVisible()
})
