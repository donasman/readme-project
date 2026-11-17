import { describe, expect, it } from "vitest"

import type { Profile } from "@/domain/profile/types"

import { certificateRenames, checkDraft, fieldId, isEmptyProfile, newCareer, newEducation, newLanguage, toDraft } from "./profile-form"

const TODAY = "2026-11-17"
const PROFILE: Profile = {
  name: "김다온",
  birthDate: "1999-03-01",
  gender: "female",
  educations: [{ school: "한국대학교", major: null, graduation: "2024-02", gpa: 3.8, gpaScale: 4.5 }],
  certificates: [{ name: "컴활", acquired: null }],
  languages: [],
  careers: [{ company: "가나다", role: "백엔드 개발", start: "2024-03", end: null }],
  careerMonths: 33,
  careerYears: 2,
}

describe("checkDraft", () => {
  it("불러온 프로필을 그대로 저장하면 같은 값을 보낸다", () => {
    const { input } = checkDraft(toDraft(PROFILE), TODAY)
    expect({ ...input, careerMonths: 33, careerYears: 2 }).toEqual(PROFILE)
  })

  it("아무것도 넣지 않은 줄은 버리고, 반만 넣은 줄은 칸을 표시한다", () => {
    const draft = toDraft(PROFILE)
    const empty = newLanguage()
    const half = { ...newEducation(), major: "경영" }
    const result = checkDraft({ ...draft, languages: [empty], educations: [...draft.educations, half] }, TODAY)
    expect(result.errors).toEqual({ [fieldId("edu", half.key, "school")]: "학교를 넣어 주세요." })
  })

  it("연도만 고른 년월, 만점보다 큰 학점, 시작보다 앞선 끝 달을 막는다", () => {
    const draft = toDraft(PROFILE)
    const edu = { ...draft.educations[0], graduation: { year: "2024", month: "" }, gpa: "4.8" }
    const career = { ...newCareer(), company: "라마바", start: { year: "2024", month: "03" }, end: { year: "2023", month: "12" } }
    const result = checkDraft({ ...draft, educations: [edu], careers: [career] }, TODAY)
    expect(result.errors).toEqual({
      [fieldId("edu", edu.key, "graduation")]: "연도와 월을 함께 골라 주세요.",
      [fieldId("edu", edu.key, "gpa")]: "학점이 만점보다 큽니다.",
      [fieldId("career", career.key, "end")]: "경력의 끝 달이 시작 달보다 앞입니다.",
    })
  })

  it("재직 중이 아니면 끝 년월이 있어야 하고, 재직 중이면 끝 달을 보내지 않는다", () => {
    const career = { ...newCareer(), company: "라마바", start: { year: "2024", month: "03" }, end: { year: "2025", month: "01" } }
    const draft = { ...toDraft(PROFILE), careers: [{ ...career, end: { year: "", month: "" } }] }
    expect(checkDraft(draft, TODAY).errors).toEqual({ [fieldId("career", career.key, "end")]: "끝 년월을 고르거나 재직 중을 표시해 주세요." })
    expect(checkDraft({ ...draft, careers: [{ ...career, working: true }] }, TODAY).input?.careers).toEqual([{ company: "라마바", role: null, start: "2024-03", end: null }])
  })

  it("앞날의 생년월일과 숫자가 아닌 학점을 막는다", () => {
    const draft = toDraft(PROFILE)
    const result = checkDraft({ ...draft, birthDate: "2026-11-18", educations: [{ ...draft.educations[0], gpa: "3,8" }] }, TODAY)
    expect(Object.values(result.errors ?? {})).toEqual(["생년월일을 확인해 주세요.", "학점은 숫자로 넣어 주세요. 예: 3.8"])
  })
})

describe("프로필 비교", () => {
  it("서버가 바꾼 자격증 이름만 고른다", () => {
    const sent = { ...PROFILE, certificates: [{ name: "컴활", acquired: null }, { name: "TOEIC", acquired: null }] }
    const saved = { ...PROFILE, certificates: [{ name: "컴퓨터활용능력", acquired: null }, { name: "TOEIC", acquired: null }] }
    expect(certificateRenames(sent, saved)).toEqual(["컴활 → 컴퓨터활용능력"])
  })

  it("이름도 목록도 없으면 빈 프로필이다", () => {
    expect(isEmptyProfile(PROFILE)).toBe(false)
    expect(isEmptyProfile({ ...PROFILE, name: null, birthDate: null, gender: null, educations: [], certificates: [], careers: [] })).toBe(true)
  })
})
