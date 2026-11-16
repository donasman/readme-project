import { expect, test } from "@playwright/test"

// 회원가입의 생년월일·성별(선택)과 동의(로그인 문서 7절 26~28번): 생년월일이나 성별을 넣으면 수집·이용 동의가 있어야 보낸다.
test("생년월일을 넣고 동의하지 않으면 막고, 동의하면 가입한다", async ({ page }) => {
  await page.goto("/signup")
  await page.getByLabel("표시 이름").fill("다온")
  await page.getByLabel("이메일").fill("new-person@example.com")
  await page.getByLabel("비밀번호").fill("readme1234")
  await page.getByLabel("생년월일 (선택)").fill("2000-01-01")
  await page.getByRole("radio", { name: "여" }).check()
  await page.getByRole("button", { name: "가입하기" }).click()

  const consent = page.getByRole("checkbox", { name: /생년월일·성별 수집·이용 동의/ })
  await expect(page.getByText("생년월일이나 성별을 넣으려면 수집·이용에 동의해 주세요.")).toBeVisible()
  await expect(consent).toHaveAttribute("aria-invalid", "true")
  await expect(page).toHaveURL(/\/signup$/)

  await consent.check()
  await page.getByRole("button", { name: "가입하기" }).click()
  await expect(page).toHaveURL(/\/signup\/sent$/)
})
