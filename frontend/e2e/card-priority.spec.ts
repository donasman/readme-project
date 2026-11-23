import { expect, test } from "@playwright/test"

// 경험카드 우선순위(카드 우선순위 문서): 내 공고 상세의 공고 내용과 메모 사이. 초안은 LLM(가짜 서버는 키워드 겹침), 그 뒤에는 손으로 고친다.
test("초안을 만들고 위·아래, 빼기, 더하기로 순서를 고친다", async ({ page }) => {
  await page.goto("/my-posts/p-pub1")
  const panel = page.getByRole("region", { name: "경험카드 우선순위" })
  const titles = panel.locator("ol > li a")
  await expect(panel.getByText("아직 고른 카드가 없습니다")).toBeVisible()

  await panel.getByRole("button", { name: "LLM으로 초안 만들기" }).click()
  await expect(panel.getByRole("button", { name: "초안 만드는 중…" })).toBeDisabled()
  await expect(panel.getByRole("button", { name: "카드 더하기" })).toBeDisabled()
  await expect(titles).toHaveCount(4)
  const first = await titles.nth(0).innerText()
  const second = await titles.nth(1).innerText()
  await expect(panel.getByText("공고 키워드 Java에 답하는 카드입니다").first()).toBeVisible()

  await panel.getByRole("button", { name: `${first} 아래로` }).click()
  await expect(titles.nth(0)).toHaveText(second)
  await expect(titles.nth(1)).toHaveText(first)
  await expect(panel.locator("p[role=status]")).toHaveText(`${first}을(를) 2번째로 옮겼습니다.`)

  await panel.getByRole("button", { name: `${second} 빼기` }).click()
  await expect(titles).toHaveCount(3)

  await panel.getByRole("button", { name: "카드 더하기" }).click()
  await page.getByRole("menuitem", { name: "알고리즘 스터디 운영" }).click()
  await expect(titles).toHaveCount(4)
  await expect(titles.nth(3)).toHaveText("알고리즘 스터디 운영")

  // 다시 만들기는 지금 순서가 바뀐다고 먼저 묻는다
  await panel.getByRole("button", { name: "LLM으로 초안 다시 만들기" }).click()
  const dialog = page.getByRole("dialog", { name: "초안으로 바꿀까요?" })
  await dialog.getByRole("button", { name: "초안 만들기" }).click()
  // 초안을 만드는 동안 창은 "만드는 중"을 보이고 목록은 고칠 수 없다(늦게 끝난 초안이 고친 순서를 덮어쓰지 않게).
  // 잠금은 창을 닫기 전에 본다: 닫히기를 기다리면 가짜 초안(600ms)이 먼저 끝날 수 있다. 창이 덮은 목록은 역할로 찾지 못해 aria-label로 찾는다
  const remove = panel.locator(`button[aria-label="${first} 빼기"]`)
  await expect(dialog.getByRole("button", { name: "만드는 중…" })).toBeDisabled()
  await expect(remove).toBeDisabled()
  await expect(panel.locator("button", { hasText: "초안 만드는 중…" })).toBeDisabled()
  await page.keyboard.press("Escape")
  await expect(dialog).toBeHidden()
  await expect(remove).toBeEnabled()
  await expect(titles.nth(0)).toHaveText(first)
})
