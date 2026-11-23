import { expect, test, type Page } from "@playwright/test"

// 자기소개서 문항과 경험카드(문항 매칭 문서 5절): 내 공고 상세의 경험카드 우선순위와 메모 사이. 카드는 LLM이 고르고(가짜 서버는 문항 글과 키워드), 그 뒤에는 손으로 고친다.
const panelOf = (page: Page) => page.getByRole("region", { name: "자기소개서 문항" })
const REUSED = "문항과 카드가 바뀌지 않아 앞서 고른 결과를 그대로 보입니다."

test("공고의 문항에 카드를 고르고, 카드 바꾸기로 고친 뒤 다시 고른다", async ({ page }) => {
  await page.goto("/my-posts/p-pub1")
  const panel = panelOf(page)
  const rows = panel.locator("ol > li")
  await expect(rows).toHaveCount(3)
  await expect(panel.getByText("공고에서 찾은 문항입니다")).toBeVisible()

  await panel.getByRole("button", { name: "LLM으로 카드 고르기" }).click()
  await expect(panel.getByRole("button", { name: "고르는 중…" })).toBeDisabled()
  await expect(panel.getByRole("button", { name: "문항 고치기" })).toBeDisabled()
  await expect(panel.getByRole("button", { name: "1번 문항 카드 바꾸기" })).toBeDisabled()
  await expect(rows.nth(1).getByText("문제해결·개선")).toBeVisible()
  await expect(rows.nth(2).getByText("협업·소통")).toBeVisible()
  await expect(rows.nth(0).getByText("지원동기")).toBeVisible()
  await expect(rows.nth(0).getByText("회사와 직무 조사가 중심인 문항이라 카드를 고르지 않았습니다.")).toBeVisible()
  await expect(panel.getByText("공고에서 찾은 문항입니다")).toBeHidden()

  // 글자 수 1,000자 문항은 주된 카드와 보조 카드 두 장
  const cards = rows.nth(1).locator("button[aria-haspopup=dialog]")
  await expect(cards).toHaveCount(2)
  const titleOf = (i: number) => cards.nth(i).locator("span.font-bold").innerText()
  const first = await titleOf(0)
  const second = await titleOf(1)
  await expect(cards.nth(0).getByText("살릴 칸")).toBeVisible()

  await cards.nth(0).click()
  const preview = page.getByRole("dialog", { name: first })
  await expect(preview.getByText("2번 문항")).toBeVisible()
  await expect(preview.getByRole("heading", { name: "살릴 칸" })).toBeVisible()
  await page.keyboard.press("Escape")
  await expect(preview).toBeHidden()

  await panel.getByRole("button", { name: "2번 문항 카드 바꾸기" }).click()
  await page.getByRole("menuitem", { name: "주된 카드와 보조 카드 바꾸기" }).click()
  await expect(cards.nth(0)).toContainText(second)
  await expect(panel.locator("p[role=status]")).toHaveText(`2번 문항의 주된 카드를 ${second}(으)로 바꿨습니다.`)

  await panel.getByRole("button", { name: `2번 문항에서 ${second} 빼기` }).click()
  await expect(cards).toHaveCount(1)
  await expect(cards.nth(0)).toContainText("주된 카드")

  await panel.getByRole("button", { name: "2번 문항 카드 바꾸기" }).click()
  await page.getByRole("menuitem", { name: /알고리즘 스터디 운영/ }).click()
  await expect(cards).toHaveCount(2)
  await expect(cards.nth(1)).toContainText("알고리즘 스터디 운영")

  // 다시 고르기는 모든 문항의 카드가 바뀐다고 먼저 묻는다. 문항과 카드가 그대로라 손으로 고친 카드가 앞 결과로 돌아간다
  await panel.getByRole("button", { name: "LLM으로 카드 고르기" }).click()
  const dialog = page.getByRole("dialog", { name: "카드를 다시 고를까요?" })
  await dialog.getByRole("button", { name: "카드 고르기" }).click()
  await expect(dialog).toBeHidden()
  await expect(cards.nth(0)).toContainText(first)
  await expect(cards.nth(1)).toContainText(second)
  await expect(panel.getByRole("paragraph").filter({ hasText: REUSED })).toBeVisible()
})

test("문항과 카드가 그대로면 앞서 고른 결과를 다시 보이고, 문항을 고치면 새로 고른다", async ({ page }) => {
  await page.goto("/my-posts/p-pub1")
  const panel = panelOf(page)
  const rows = panel.locator("ol > li")
  const cardTitles = () => panel.locator("button[aria-haspopup=dialog] span.font-bold").allInnerTexts()
  await panel.getByRole("button", { name: "LLM으로 카드 고르기" }).click()
  await expect(rows.nth(2).getByText("협업·소통")).toBeVisible()
  const matched = await cardTitles()

  const dialog = page.getByRole("dialog", { name: "카드를 다시 고를까요?" })
  await panel.getByRole("button", { name: "LLM으로 카드 고르기" }).click()
  await expect(dialog.getByText("문항이나 카드가 바뀌었으면 이번 달 LLM 사용액이 듭니다.")).toBeVisible()
  await dialog.getByRole("button", { name: "카드 고르기" }).click()
  await expect(dialog).toBeHidden()
  await expect(panel.getByRole("paragraph").filter({ hasText: REUSED })).toBeVisible()
  await expect(panel.locator("p[role=status]")).toHaveText(REUSED)
  expect(await cardTitles()).toEqual(matched)
  // 알림 줄이 휴대폰 폭에서 옆으로 넘치지 않는다
  expect(await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)).toBeLessThanOrEqual(0)

  // 문항 글을 고치면 알림을 지우고, 다시 고르면 LLM이 새로 고른다
  await panel.getByRole("button", { name: "문항 고치기" }).click()
  const editor = page.getByRole("dialog", { name: "문항 고치기" })
  await editor.getByRole("textbox", { name: "2번 문항", exact: true }).fill("직무와 관련해 문제를 해결하고 개선한 경험을 기술하시오")
  await editor.getByRole("button", { name: "저장" }).click()
  await expect(editor).toBeHidden()
  await expect(panel.getByRole("paragraph").filter({ hasText: REUSED })).toBeHidden()

  await panel.getByRole("button", { name: "LLM으로 카드 고르기" }).click()
  await dialog.getByRole("button", { name: "카드 고르기" }).click()
  // 고르는 동안 창은 "고르는 중"을 보인다
  await expect(dialog.getByRole("button", { name: "고르는 중…" })).toBeDisabled()
  await expect(dialog).toBeHidden()
  await expect(rows.nth(1).getByText("문제해결·개선")).toBeVisible()
  await expect(panel.getByRole("paragraph").filter({ hasText: REUSED })).toBeHidden()
})

test("문항 고치기 창에서 빼고, 더하고, 순서와 글자 수를 바꾼다", async ({ page }) => {
  await page.goto("/my-posts/p-pub1")
  const panel = panelOf(page)
  const rows = panel.locator("ol > li")
  await panel.getByRole("button", { name: "문항 고치기" }).click()
  const dialog = page.getByRole("dialog", { name: "문항 고치기" })
  await expect(dialog.getByText("공고에서 찾은 문항입니다. 저장하면 이 목록을 씁니다.")).toBeVisible()

  await dialog.getByRole("button", { name: "1번 문항 빼기" }).click()
  await dialog.getByRole("textbox", { name: "1번 문항 글자 수" }).fill("300")
  await dialog.getByRole("button", { name: "문항 더하기" }).click()
  await dialog.getByRole("button", { name: "저장" }).click()
  await expect(dialog.getByText("문항 글을 넣어 주세요.")).toBeVisible()

  await dialog.getByRole("textbox", { name: "3번 문항", exact: true }).fill("성격의 장단점을 쓰시오")
  // 숫자가 아닌 글자 수는 빈 값(글자 수 없음)으로 넘어가지 않고 막힌다
  const limit = dialog.getByRole("textbox", { name: "3번 문항 글자 수" })
  await limit.fill("1e3")
  await dialog.getByRole("button", { name: "저장" }).click()
  await expect(dialog.getByText("글자 수는 1~10,000 사이 숫자로 넣거나 비워 두세요.")).toBeVisible()
  await limit.fill("0")
  await expect(dialog.getByText("글자 수는 1~10,000 사이 숫자로 넣거나 비워 두세요.")).toBeVisible()
  await limit.fill("")
  await dialog.getByRole("button", { name: "3번 문항 위로" }).click()
  await dialog.getByRole("button", { name: "저장" }).click()
  await expect(dialog).toBeHidden()

  await expect(rows).toHaveCount(3)
  await expect(rows.nth(0)).toContainText("직무와 관련해 문제를 해결한 경험을 기술하시오")
  await expect(rows.nth(0)).toContainText("300자")
  await expect(rows.nth(1)).toContainText("성격의 장단점을 쓰시오")
  await expect(rows.nth(1)).toContainText("글자 수 없음")
  await expect(panel.getByText("공고에서 찾은 문항입니다")).toBeHidden()
  await expect(panel.locator("p[role=status]")).toHaveText("문항 3개를 저장했습니다.")

  // 500자 아래 문항은 카드 한 장
  await panel.getByRole("button", { name: "LLM으로 카드 고르기" }).click()
  await expect(rows.nth(1).getByText("가치관·성격")).toBeVisible()
  await expect(rows.nth(0).locator("button[aria-haspopup=dialog]")).toHaveCount(1)
  await expect(rows.nth(1).locator("button[aria-haspopup=dialog]")).toHaveCount(2)
})

test("공고에 문항이 없으면 지원서의 문항을 넣는다", async ({ page }) => {
  await page.goto("/my-posts/p-be2")
  const panel = panelOf(page)
  await expect(panel.getByText("공고에서 문항을 찾지 못했습니다. 지원서의 문항을 넣어 주세요.")).toBeVisible()
  await panel.getByRole("button", { name: "문항 넣기" }).click()
  const dialog = page.getByRole("dialog", { name: "문항 넣기" })
  // 처음부터 문항이 없던 공고는 "모두 뺍니다"를 알리지 않는다
  await expect(dialog.getByRole("button", { name: "문항 더하기" })).toBeVisible()
  await expect(dialog.getByText("저장하면 이 공고의 문항을 모두 뺍니다")).toHaveCount(0)
  await dialog.getByRole("button", { name: "문항 더하기" }).click()
  await dialog.getByRole("textbox", { name: "1번 문항", exact: true }).fill("입사 후 포부를 쓰시오")
  await dialog.getByRole("textbox", { name: "1번 문항 글자 수" }).fill("700")
  await dialog.getByRole("button", { name: "저장" }).click()
  await expect(dialog).toBeHidden()
  await expect(panel.locator("ol > li")).toHaveCount(1)
  await expect(panel.getByText("700자")).toBeVisible()

  await panel.getByRole("button", { name: "LLM으로 카드 고르기" }).click()
  await expect(panel.getByText("입사 후 포부", { exact: true })).toBeVisible()
  await expect(panel.getByText("회사와 직무 조사가 중심인 문항이라 카드를 고르지 않았습니다.")).toBeVisible()
})

test("고르기 전에 저장 전 문항에 카드를 더하면 문항을 먼저 저장한다", async ({ page }) => {
  await page.goto("/my-posts/p-pub1")
  const panel = panelOf(page)
  const rows = panel.locator("ol > li")
  await expect(panel.getByText("공고에서 찾은 문항입니다")).toBeVisible()
  await panel.getByRole("button", { name: "1번 문항 카드 바꾸기" }).click()
  await page.getByRole("menuitem", { name: /알고리즘 스터디 운영/ }).click()
  await expect(rows.nth(0).locator("button[aria-haspopup=dialog]")).toHaveCount(1)
  await expect(rows.nth(0).locator("button[aria-haspopup=dialog]")).toContainText("알고리즘 스터디 운영")
  await expect(panel.getByText("공고에서 찾은 문항입니다")).toBeHidden()
  await expect(panel.locator("p[role=status]")).toHaveText("1번 문항에 알고리즘 스터디 운영을(를) 더했습니다.")
})
