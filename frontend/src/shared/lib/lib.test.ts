import type { ShouldRevalidateFunctionArgs } from "react-router"
import { describe, expect, it } from "vitest"

import { addDays, daysBetween, formatCommentTime, formatDateWeekday, formatDday, formatLongDate, formatMonthDay, formatMonthDayWeekday, seoulToday } from "./date"
import { NotFoundError } from "./errors"
import { objectParticle, subjectParticle } from "./korean"
import { paths } from "./paths"
import { revalidateOnParams } from "./router"
import { firstChar, isHttpUrl, matchesAccept } from "./utils"

describe("date", () => {
  it("daysBetween: 달과 해를 넘어도 날 수", () => {
    expect(daysBetween("2026-10-30", "2026-11-02")).toBe(3)
    expect(daysBetween("2026-12-31", "2027-01-01")).toBe(1)
    expect(daysBetween("2026-11-13", "2026-11-10")).toBe(-3)
  })
  it("seoulToday: UTC로는 전날이어도 한국 날짜", () => {
    expect(seoulToday(new Date("2026-11-12T15:30:00Z"))).toBe("2026-11-13")
    expect(seoulToday(new Date("2026-12-31T14:59:59Z"))).toBe("2026-12-31")
  })
  it("addDays: 달과 해를 넘긴다", () => {
    expect(addDays("2026-11-13", 7)).toBe("2026-11-20")
    expect(addDays("2026-12-28", 7)).toBe("2027-01-04")
  })
  it("D-day: 남은 날, 당일은 D-Day", () => {
    expect(formatDday("2026-11-13", "2026-11-16")).toBe("D-3")
    expect(formatDday("2026-11-13", "2026-11-13")).toBe("D-Day")
  })
  it("날짜와 요일", () => {
    expect(formatDateWeekday("2026-11-18")).toBe("2026-11-18 (수)")
  })
  it("긴 날짜: 연월일과 요일", () => {
    expect(formatLongDate("2026-11-13")).toBe("2026년 11월 13일 금요일")
  })
  it("월.일과 요일", () => {
    expect(formatMonthDay("2026-03-05")).toBe("03.05")
    expect(formatMonthDayWeekday("2026-11-13")).toBe("11.13 (금)")
  })
  it("댓글 시각: 1분 안이면 방금, 아니면 한국 날짜", () => {
    const now = Date.parse("2026-11-13T03:00:00Z")
    expect(formatCommentTime("2026-11-13T02:59:30Z", now)).toBe("방금")
    // 11-12 16:00 UTC = 11-13 01:00 KST
    expect(formatCommentTime("2026-11-12T16:00:00Z", now)).toBe("11.13")
  })
})

describe("korean", () => {
  it("주격 조사", () => {
    expect(subjectParticle("가람")).toBe("이")
    expect(subjectParticle("나래")).toBe("가")
    expect(subjectParticle("API")).toBe("이(가)")
  })
  it("목적격 조사", () => {
    expect(objectParticle("그룹")).toBe("을")
    expect(objectParticle("공고")).toBe("를")
    expect(objectParticle("")).toBe("을(를)")
  })
  it("NotFoundError 메시지", () => {
    expect(new NotFoundError("그룹").message).toBe("그룹을 찾을 수 없습니다.")
    expect(new NotFoundError("공고").message).toBe("공고를 찾을 수 없습니다.")
  })
})

describe("paths", () => {
  it("주소를 만들고 값은 인코딩한다", () => {
    expect(paths.groupPosts("be")).toBe("/groups/be/posts")
    expect(paths.groupPost("be", "be1")).toBe("/groups/be/posts/be1")
    expect(paths.groupPost("a b", "c/d")).toBe("/groups/a%20b/posts/c%2Fd")
  })
})

describe("revalidateOnParams", () => {
  const args = (from: string, to: string, over: Partial<ShouldRevalidateFunctionArgs> = {}): ShouldRevalidateFunctionArgs => {
    const currentUrl = new URL(from, "http://x")
    const nextUrl = new URL(to, "http://x")
    const params = (url: URL) => {
      const [, , groupId, , postId] = url.pathname.split("/")
      return { ...(groupId && { groupId }), ...(postId && { postId }) }
    }
    return { currentUrl, nextUrl, currentParams: params(currentUrl), nextParams: params(nextUrl), defaultShouldRevalidate: true, ...over }
  }
  const group = revalidateOnParams("groupId")
  const root = revalidateOnParams()

  it("? 뒤만 바뀌면 다시 읽지 않는다", () => {
    expect(group(args("/groups/be/posts", "/groups/be/posts?q=a"))).toBe(false)
    expect(root(args("/groups/be/posts", "/groups/be/posts?mine=1"))).toBe(false)
  })
  it("쓰는 값이 그대로면 아래 화면이 바뀌어도 다시 읽지 않는다", () => {
    expect(group(args("/groups/be/posts?mine=1", "/groups/be/posts/be1"))).toBe(false)
    expect(root(args("/groups/be/posts", "/groups/pub/posts"))).toBe(false)
  })
  it("쓰는 값이 바뀌면 다시 읽는다", () => {
    expect(group(args("/groups/be/posts", "/groups/pub/posts"))).toBe(true)
  })
  it("같은 주소(저장 뒤 revalidate 등)와 제출은 라우터 기본 판단을 따른다", () => {
    expect(group(args("/groups/be/posts?q=a", "/groups/be/posts?q=a"))).toBe(true)
    expect(group(args("/groups/be/posts?q=a", "/groups/be/posts?q=a", { defaultShouldRevalidate: false }))).toBe(false)
    expect(group(args("/groups/be/posts", "/groups/be/posts/be1", { formMethod: "POST" }))).toBe(true)
  })
})

describe("isHttpUrl", () => {
  it("http·https 주소만 받는다", () => {
    expect(isHttpUrl("https://careers.example.com/jobs/7")).toBe(true)
    expect(isHttpUrl("careers.example.com/jobs/7")).toBe(false)
    expect(isHttpUrl("javascript:alert(1)")).toBe(false)
  })
})

describe("matchesAccept", () => {
  it("accept의 확장자로 끝나는 파일만 받는다", () => {
    expect(matchesAccept("공고.PDF", ".pdf,.docx")).toBe(true)
    expect(matchesAccept("photo.png", ".pdf,.docx")).toBe(false)
    expect(matchesAccept("report.pdf.exe", ".pdf,.docx")).toBe(false)
  })
})

describe("firstChar", () => {
  it("이모지와 조합형 한글도 한 글자로 자른다", () => {
    expect(firstChar("다온")).toBe("다")
    expect(firstChar("🐳 스터디")).toBe("🐳")
    expect(firstChar("👨‍👩‍👧 가족")).toBe("👨‍👩‍👧")
    expect(firstChar("다온".normalize("NFD"))).toBe("다".normalize("NFD"))
    expect(firstChar("")).toBe("")
  })
})
