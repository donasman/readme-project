import { expect, test } from "@playwright/test"

const REUSED = "공고와 카드가 바뀌지 않아 앞서 만든 초안을 그대로 보입니다."

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

  // 다시 만들기는 지금 순서가 바뀐다고 먼저 묻는다. 공고와 카드가 그대로라 손으로 고친 순서가 앞 초안으로 돌아간다
  await panel.getByRole("button", { name: "LLM으로 초안 다시 만들기" }).click()
  const dialog = page.getByRole("dialog", { name: "초안으로 바꿀까요?" })
  await dialog.getByRole("button", { name: "초안 만들기" }).click()
  await expect(dialog).toBeHidden()
  await expect(titles.nth(0)).toHaveText(first)
  await expect(titles.nth(1)).toHaveText(second)
  await expect(panel.getByRole("paragraph").filter({ hasText: REUSED })).toBeVisible()
})

test("공고와 카드가 그대로면 앞 초안을 다시 보이고, 카드를 고치면 새로 만든다", async ({ page }) => {
  await page.goto("/my-posts/p-pub1")
  const panel = page.getByRole("region", { name: "경험카드 우선순위" })
  const titles = panel.locator("ol > li a")
  await panel.getByRole("button", { name: "LLM으로 초안 만들기" }).click()
  await expect(titles).toHaveCount(4)
  const drafted = await titles.allInnerTexts()

  const dialog = page.getByRole("dialog", { name: "초안으로 바꿀까요?" })
  await panel.getByRole("button", { name: "LLM으로 초안 다시 만들기" }).click()
  await expect(dialog.getByText("공고나 카드가 바뀌었으면 이번 달 LLM 사용액이 듭니다.")).toBeVisible()
  await dialog.getByRole("button", { name: "초안 만들기" }).click()
  await expect(dialog).toBeHidden()
  await expect(titles).toHaveText(drafted)
  // 알림 줄이 휴대폰 폭에서 옆으로 넘치지 않는다
  expect(await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)).toBeLessThanOrEqual(0)
  await expect(panel.getByRole("paragraph").filter({ hasText: REUSED })).toBeVisible()
  await expect(panel.locator("p[role=status]")).toHaveText(REUSED)

  // 목록을 고치면 알림을 지운다
  await panel.getByRole("button", { name: `${drafted[0]} 아래로` }).click()
  await expect(panel.getByRole("paragraph").filter({ hasText: REUSED })).toBeHidden()

  // 카드를 고치고(내 공고 → 카드 → 수정 → 저장한 카드) 세 번 뒤로 돌아온다
  await titles.nth(0).click()
  await page.getByRole("link", { name: "수정" }).click()
  await page.getByLabel("제목").fill(`${drafted[1]} 고침`)
  await page.getByRole("button", { name: "저장" }).click()
  await expect(page.getByRole("heading", { name: `${drafted[1]} 고침`, level: 1 })).toBeVisible()
  await page.goBack()
  await page.goBack()
  await page.goBack()
  await expect(titles.nth(0)).toHaveText(`${drafted[1]} 고침`)

  await panel.getByRole("button", { name: "LLM으로 초안 다시 만들기" }).click()
  await dialog.getByRole("button", { name: "초안 만들기" }).click()
  // 초안을 만드는 동안 창은 "만드는 중"을 보이고 목록은 고칠 수 없다(늦게 끝난 초안이 고친 순서를 덮어쓰지 않게).
  // 잠금은 창을 닫기 전에 본다: 닫히기를 기다리면 가짜 초안(600ms)이 먼저 끝날 수 있다. 창이 덮은 목록은 역할로 찾지 못해 aria-label로 찾는다
  const remove = panel.locator(`button[aria-label="${drafted[0]} 빼기"]`)
  await expect(dialog.getByRole("button", { name: "만드는 중…" })).toBeDisabled()
  await expect(remove).toBeDisabled()
  await expect(panel.locator("button", { hasText: "초안 만드는 중…" })).toBeDisabled()
  await page.keyboard.press("Escape")
  await expect(dialog).toBeHidden()
  await expect(remove).toBeEnabled()
  await expect(titles.nth(0)).toHaveText(drafted[0])
  await expect(panel.getByRole("paragraph").filter({ hasText: REUSED })).toBeHidden()
})
