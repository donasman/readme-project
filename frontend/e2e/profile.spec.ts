import { expect, test } from "@playwright/test"

// 기본 프로필(12차 시안): 틀린 칸은 저장 전에 표시하고, 고쳐서 저장하면 서버가 대표 표기로 바꾼 자격증 이름을 알린다.
test("틀린 칸을 고쳐 저장하면 대표 표기로 바꾼 이름을 알린다", async ({ page }) => {
  await page.goto("/settings/profile")
  const education = page.getByRole("group", { name: "학력 1" })
  await education.getByLabel("학점").fill("4.8")
  await page.getByRole("group", { name: "자격증 2" }).getByLabel("이름").fill("컴활")
  await page.getByRole("button", { name: "저장" }).click()
  await expect(page.getByRole("alert")).toHaveText("저장하지 못했습니다. 표시한 칸 1곳을 고쳐 주세요.")
  await expect(education.getByLabel("학점")).toHaveAttribute("aria-invalid", "true")

  await education.getByLabel("학점").fill("3.9")
  await page.getByRole("button", { name: "저장" }).click()
  await expect(page.getByRole("status").filter({ hasText: "저장했습니다." })).toHaveText("저장했습니다. 자격증 이름 하나를 대표 표기로 바꿨습니다(컴활 → 컴퓨터활용능력).")
  await expect(page.getByRole("group", { name: "자격증 2" }).getByLabel("이름")).toHaveValue("컴퓨터활용능력")
})

test("재직 중을 끄면 끝 년월을 고르게 한다", async ({ page }) => {
  await page.goto("/settings/profile")
  const career = page.getByRole("group", { name: "경력 1" })
  await career.getByLabel("재직 중").uncheck()
  await page.getByRole("button", { name: "저장" }).click()
  await expect(career.getByText("끝 년월을 고르거나 재직 중을 표시해 주세요.")).toBeVisible()
  const end = career.getByRole("group", { name: "끝 년월" })
  await end.getByLabel("연도").selectOption("2025")
  await end.getByLabel("월").selectOption("06")
  await page.getByRole("button", { name: "저장" }).click()
  await expect(page.getByRole("status").filter({ hasText: "저장했습니다." })).toBeVisible()
  await expect(page.getByText("연차 1년 (16개월)")).toBeVisible()
})
