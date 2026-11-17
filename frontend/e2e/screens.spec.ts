import { expect, test } from "@playwright/test"

// 화면 점검: 주요 화면이 콘솔 오류 없이 열리고, 탭 제목이 그 화면을 말하고, 옆으로 넘치지 않는다. 픽셀 비교는 npm run regress가 한다.
// 제목은 그 화면에만 있는 것으로 고른다. 오류 화면에도 제목이 있어서, 아무 제목이나 보면 깨진 화면도 통과한다.
const SCREENS = [
  ["/welcome", "리드미에서 하는 일", "취업준비생을 위한 정보공유·스터디 · 리드미"],
  ["/", "다가오는 마감", "홈 · 리드미"],
  ["/groups/be/posts", "공고", "공고 · 백엔드 스터디 · 리드미"],
  ["/groups/be/posts/be5", "공고 내용", "백엔드 엔지니어 · 백엔드 스터디 · 리드미"],
  ["/my-posts", "내 공고", "내 공고 · 리드미"],
  ["/board", "지원 현황", "지원 현황 · 리드미"],
  ["/cards", "경험카드", "경험카드 · 리드미"],
  ["/settings/account", "로그인 방법", "내 정보 · 설정 · 리드미"],
  ["/settings/profile", "인적사항", "기본 프로필 · 설정 · 리드미"],
]

for (const [path, heading, title] of SCREENS) {
  test(`${path} 화면`, async ({ page }, testInfo) => {
    const errors: string[] = []
    page.on("console", (message) => {
      if (message.type() === "error") errors.push(message.text())
    })
    page.on("pageerror", (error) => errors.push(error.message))

    await page.goto(path)
    await expect(page.getByRole("heading", { name: heading, exact: true })).toBeVisible()
    await expect(page).toHaveTitle(title)

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow, "옆으로 넘친 폭(px)").toBeLessThanOrEqual(0)

    // 머리 줄의 링크와 버튼은 한 줄에 있다(휴대폰에서 내 계정이 둘째 줄로 떨어지지 않는다)
    const tops = await page
      .locator("header")
      .first()
      .locator("a, button")
      .evaluateAll((els) => els.filter((el) => el.getBoundingClientRect().width > 0).map((el) => el.getBoundingClientRect().top))
    expect(Math.max(...tops) - Math.min(...tops), "머리 줄 항목의 높이 차(px)").toBeLessThanOrEqual(12)

    // 데스크톱에서는 표나 상자 안에서도 옆으로 밀어야 보이는 것이 없다(지원 현황의 "바꾸기"가 잘렸었다)
    if (testInfo.project.name === "desktop") {
      const clipped = await page.evaluate(() =>
        [...document.querySelectorAll("main *")]
          .filter((el) => ["auto", "scroll"].includes(getComputedStyle(el).overflowX) && el.scrollWidth > el.clientWidth + 1)
          .map((el) => `${el.tagName} ${el.scrollWidth}/${el.clientWidth}`)
      )
      expect(clipped, "안쪽 가로 스크롤").toEqual([])
    }

    expect(errors).toEqual([])
  })
}
