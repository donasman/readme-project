import { describe, expect, it } from "vitest"

import type { Profile } from "@/domain/profile/types"

import { profileCheckRows } from "./profile-check"

const EMPTY: Profile = { name: null, birthDate: null, gender: null, educations: [], certificates: [], languages: [], careers: [], careerMonths: 0, careerYears: 0 }
const AFTER: Profile = {
  ...EMPTY,
  name: "김다온",
  educations: [{ school: "한국대학교", major: "컴퓨터공학", graduation: "2024-02", gpa: 3.8, gpaScale: 4.5 }],
  certificates: [{ name: "정보처리기사", acquired: null }],
  careers: [{ company: "가나다", role: "백엔드 개발", start: "2024-03", end: null }],
  careerMonths: 33,
  careerYears: 2,
}

describe("profileCheckRows", () => {
  it("뽑기 전과 달라진 칸만 이번에 채운 칸으로 본다", () => {
    const rows = profileCheckRows(AFTER, { ...EMPTY, name: "김다온" })
    expect(rows.map((r) => [r.label, r.filled])).toEqual([
      ["이름", false],
      ["생년월일", false],
      ["성별", false],
      ["학력", true],
      ["자격증", true],
      ["어학", false],
      ["경력", true],
    ])
  })

  it("값을 줄로 풀고, 빈 칸은 줄이 없다", () => {
    const rows = Object.fromEntries(profileCheckRows(AFTER, null).map((r) => [r.label, r]))
    expect(rows["학력"].lines).toEqual([{ value: "한국대학교 컴퓨터공학", note: "2024-02 졸업 · 학점 3.8 / 4.5" }])
    expect(rows["자격증"].lines).toEqual([{ value: "정보처리기사", note: null }])
    expect(rows["경력"]).toMatchObject({ lines: [{ value: "가나다 · 백엔드 개발", note: "2024-03부터 재직 중" }], extra: "연차 2년 (33개월)", filled: false })
    expect(rows["어학"].lines).toEqual([])
  })
})
