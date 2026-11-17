import { expect, test } from "@playwright/test"

// 내 정보의 구글 연결(로그인 문서 7절 22~25번): 구글에서 ?google=결과를 들고 돌아오면 안내를 보이고 주소에서 그 값만 지운다.
test("구글 연결에서 돌아오면 결과를 알리고 주소에서 지운다", async ({ page }) => {
  await page.goto("/settings/account?mock=no-google&google=in-use")
  await expect(page.getByText("이 구글 계정은 다른 리드미 계정에 연결되어 있습니다.").first()).toBeVisible()
  // google=만 지우고 다른 값(가짜 서버의 mock=)은 남긴다
  await expect(page).toHaveURL(/\/settings\/account\?mock=no-google$/)
  await expect(page.getByRole("status").filter({ hasText: "이 구글 계정은 다른 리드미 계정에 연결되어 있습니다." })).toHaveCount(1)
})

test("구글 연결을 누르면 연결한 채로 내 정보로 돌아온다(가짜 서버)", async ({ page }) => {
  await page.goto("/settings/account?mock=no-google")
  await page.getByRole("button", { name: "구글 연결" }).click()
  await expect(page.getByText("구글 계정을 연결했습니다.").first()).toBeVisible()
  await expect(page.getByText("연결됨")).toBeVisible()
  await expect(page).toHaveURL(/\/settings\/account$/)
})
