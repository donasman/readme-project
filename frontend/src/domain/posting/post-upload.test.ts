import { describe, expect, it } from "vitest"

import { postCheckErrors, toPostUpload, type PostCheckValues } from "./post-upload"

const values = (over: Partial<PostCheckValues> = {}): PostCheckValues => ({
  company: "예시 커머스",
  title: "백엔드 개발자 신입",
  dueMode: "date",
  dueDate: "2026-11-30",
  sourceUrl: "",
  applyAt: "",
  sections: [],
  keywords: ["Java"],
  ...over,
})

describe("postCheckErrors", () => {
  it("다 채우면 막지 않는다", () => {
    expect(postCheckErrors(values())).toEqual({})
  })

  it("공백만 넣은 회사명·공고명, 날짜 없는 마감, http가 아닌 링크를 막는다", () => {
    expect(postCheckErrors(values({ company: "  ", title: "", dueDate: "", sourceUrl: "example.com/job" }))).toEqual({
      company: "회사명을 넣어 주세요.",
      title: "공고명을 넣어 주세요.",
      deadline: "마감일을 넣거나 상시, 모름을 고르세요.",
      sourceUrl: "https:// 로 시작하는 주소를 넣거나 비워 두세요.",
    })
  })

  it("상시·모름은 날짜가 없어도 된다", () => {
    expect(postCheckErrors(values({ dueMode: "always", dueDate: "" }))).toEqual({})
    expect(postCheckErrors(values({ dueMode: "unknown", dueDate: "" }))).toEqual({})
  })
})

describe("toPostUpload", () => {
  it("앞뒤 공백을 지우고 빈 링크와 지원처는 null로 보낸다", () => {
    const upload = toPostUpload(values({ company: " 예시 커머스 ", title: "백엔드 개발자 신입\n", sourceUrl: "  ", applyAt: " 채용 사이트 " }))
    expect(upload).toMatchObject({ company: "예시 커머스", title: "백엔드 개발자 신입", sourceUrl: null, applyAt: "채용 사이트", keywords: ["Java"] })
  })

  it("날짜를 고르면 날짜를, 상시·모름이면 날짜 없이 보낸다", () => {
    expect(toPostUpload(values()).deadline).toEqual({ kind: "date", date: "2026-11-30" })
    expect(toPostUpload(values({ dueMode: "always", dueDate: "2026-11-30" })).deadline).toEqual({ kind: "always" })
  })

  it("공고 내용은 줄마다 나누고 빈 줄과 줄이 없는 묶음을 뺀다", () => {
    const upload = toPostUpload(
      values({
        sections: [
          { title: "주요 업무", text: " API 개발 \n\n  \n운영 " },
          { title: "우대 사항", text: "\n  \n" },
        ],
      }),
    )
    expect(upload.sections).toEqual([{ title: "주요 업무", items: ["API 개발", "운영"] }])
  })
})
