import { describe, expect, it } from "vitest"

import { exampleTranscript } from "./grade-example"
import {
  allText,
  applyEdits,
  categoryLabel,
  convertGpa,
  countedCourses,
  courseLine,
  formatGpa,
  gradePoint,
  gradeStats,
  groupCourses,
  groupText,
  isMajor,
  needsGradeCheck,
  normalizeGrade,
  parseCredit,
  semesterCount,
  toHundred,
  transcriptFileProblem,
  uncertainCount,
  uncertainFields,
} from "./grade-rules"
import type { TranscriptCourse } from "./types"

const course = (over: Partial<TranscriptCourse>): TranscriptCourse => ({
  year: 1,
  term: "1",
  category: "전공필수",
  name: "과목",
  credit: 3,
  grade: "A0",
  retake: null,
  unsure: [],
  ...over,
})

const MB = 1024 * 1024

describe("등급별 평점", () => {
  it("4.5 만점과 4.3 만점의 표", () => {
    expect(["A+", "A0", "B+", "B0", "C+", "C0", "D+", "D0", "F"].map((g) => gradePoint(g, 4.5))).toEqual([4.5, 4, 3.5, 3, 2.5, 2, 1.5, 1, 0])
    expect(["A+", "A0", "A-", "B+", "B0", "B-", "C+", "C0", "C-", "D+", "D0", "D-", "F"].map((g) => gradePoint(g, 4.3))).toEqual([
      4.3, 4, 3.7, 3.3, 3, 2.7, 2.3, 2, 1.7, 1.3, 1, 0.7, 0,
    ])
  })

  it("4.5 만점에 없는 등급(A- 등)과 읽지 못한 등급은 평점이 없고 확인할 등급이다", () => {
    expect(gradePoint("A-", 4.5)).toBeUndefined()
    expect(needsGradeCheck("A-", 4.5)).toBe(true)
    expect(needsGradeCheck("A-", 4.3)).toBe(false)
    expect(needsGradeCheck("?", 4.3)).toBe(true)
    expect(needsGradeCheck("", 4.5)).toBe(true)
  })

  it("P, S, NP, U는 평점이 없지만 확인할 등급이 아니다", () => {
    for (const g of ["P", "S", "NP", "U"]) {
      expect(gradePoint(g, 4.5)).toBeUndefined()
      expect(needsGradeCheck(g, 4.5)).toBe(false)
    }
  })
})

describe("normalizeGrade", () => {
  it("대문자로 바꾸고 기호가 없으면 0을 붙인다", () => {
    expect(normalizeGrade(" a+ ")).toBe("A+")
    expect(normalizeGrade("A")).toBe("A0")
    expect(normalizeGrade("b")).toBe("B0")
    expect(normalizeGrade("Ao")).toBe("A0")
    expect(normalizeGrade("d0")).toBe("D0")
    expect(normalizeGrade("np")).toBe("NP")
    expect(normalizeGrade("f")).toBe("F")
    expect(normalizeGrade("b-")).toBe("B-")
  })
})

describe("gradeStats", () => {
  it("평점평균은 학점으로 무게를 준다", () => {
    const stats = gradeStats([course({ credit: 3, grade: "A+" }), course({ credit: 1, grade: "B0" })], 4.5)
    expect(stats.credits).toBe(4)
    expect(stats.gpa).toBeCloseTo((4.5 * 3 + 3 * 1) / 4)
  })

  it("P, S는 취득학점에만, NP, U는 어디에도 넣지 않는다", () => {
    const stats = gradeStats([course({ grade: "A0" }), course({ credit: 2, grade: "P" }), course({ credit: 1, grade: "S" }), course({ grade: "NP" }), course({ grade: "U" })], 4.5)
    expect(stats).toEqual({ credits: 6, gpa: 4 })
  })

  it("F는 평점에 0으로 넣고 취득학점에서 뺀다", () => {
    expect(gradeStats([course({ grade: "A+" }), course({ grade: "F" })], 4.5)).toEqual({ credits: 3, gpa: 2.25 })
  })

  it("표에 없는 등급은 평점에서 빠지고 취득학점에는 든다", () => {
    expect(gradeStats([course({ grade: "A0" }), course({ grade: "A-" })], 4.5)).toEqual({ credits: 6, gpa: 4 })
    expect(gradeStats([course({ grade: "A0" }), course({ grade: "A-" })], 4.3).gpa).toBeCloseTo(3.85)
  })

  it("평점을 매길 과목이 없으면 평점은 null이다", () => {
    expect(gradeStats([], 4.5)).toEqual({ credits: 0, gpa: null })
    expect(gradeStats([course({ grade: "P" })], 4.5)).toEqual({ credits: 3, gpa: null })
  })
})

describe("자릿수와 환산", () => {
  it("평점은 둘째 자리, 100점은 첫째 자리 아래를 버린다", () => {
    expect(formatGpa(3.876)).toBe("3.87")
    expect(formatGpa(4)).toBe("4.00")
    expect(toHundred(4, 4.5)).toBe("88.8")
    expect(toHundred(4.5, 4.5)).toBe("100.0")
  })

  it("계산 오차로 한 자리 내려가지 않는다", () => {
    // 0.29 * 100은 28.999…다
    expect(formatGpa(0.29)).toBe("0.29")
    expect(formatGpa(4.35)).toBe("4.35")
  })

  it("다른 만점은 단순 비례로 환산한다", () => {
    expect(convertGpa(4.5, 4.5, 4.3)).toBe("4.30")
    expect(convertGpa(4.3, 4.3, 4.5)).toBe("4.50")
    expect(convertGpa(4, 4.5, 4.3)).toBe("3.82")
    expect(toHundred(4, 4.3)).toBe("93.0")
  })

  it("평점이 없으면 -", () => {
    expect(formatGpa(null)).toBe("-")
    expect(convertGpa(null, 4.5, 4.3)).toBe("-")
    expect(toHundred(null, 4.5)).toBe("-")
  })
})

describe("재수강과 전공", () => {
  it("재수강 전 성적 빼기가 켜져 있으면 before 줄만 뺀다", () => {
    const list = [course({ retake: "before" }), course({ retake: "after" }), course({})]
    expect(countedCourses(list, true)).toHaveLength(2)
    expect(countedCourses(list, true).some((c) => c.retake === "before")).toBe(false)
    expect(countedCourses(list, false)).toHaveLength(3)
  })

  it("전공은 이수구분이 전공으로 시작한다", () => {
    expect(isMajor(course({ category: "전공필수" }))).toBe(true)
    expect(isMajor(course({ category: "전공기초" }))).toBe(true)
    expect(isMajor(course({ category: "교양선택" }))).toBe(false)
    expect(isMajor(course({ category: "복수전공" }))).toBe(false)
  })
})

describe("groupCourses", () => {
  const list = [
    course({ name: "가", year: 2, term: "1", category: "교양선택" }),
    course({ name: "나", year: 1, term: "2", category: "전공선택" }),
    course({ name: "다", year: 1, term: "여름", category: "자유선택" }),
    course({ name: "라", year: 1, term: "1", category: "전공필수" }),
    course({ name: "마", year: 1, term: "겨울", category: "전공선택" }),
  ]
  const names = (by: "term" | "year" | "category") => groupCourses(list, by).map((g) => [g.title, g.courses.map((c) => c.name).join("")])

  it("학기별: 학년, 그 안에서 1학기·여름·2학기·겨울 순서", () => {
    expect(names("term")).toEqual([
      ["1학년 1학기", "라"],
      ["1학년 여름학기", "다"],
      ["1학년 2학기", "나"],
      ["1학년 겨울학기", "마"],
      ["2학년 1학기", "가"],
    ])
  })

  it("학년별: 학년 순서, 묶음 안은 나온 순서", () => {
    expect(names("year")).toEqual([
      ["1학년", "나다라마"],
      ["2학년", "가"],
    ])
  })

  it("이수구분별: 정한 순서 다음에 나머지", () => {
    expect(names("category")).toEqual([
      ["전공필수", "라"],
      ["전공선택", "나마"],
      ["교양선택", "가"],
      ["자유선택", "다"],
    ])
  })

  it("과목이 없으면 묶음도 없다", () => {
    expect(groupCourses([], "term")).toEqual([])
  })

  it("서버가 읽지 못한 이수구분과 학기(빈 글)는 없음으로 묶는다", () => {
    const blank = [course({ category: "", term: "" })]
    expect(groupCourses(blank, "category").map((g) => g.title)).toEqual(["이수구분 없음"])
    expect(groupCourses(blank, "term").map((g) => g.title)).toEqual(["1학년 학기 없음"])
    expect(categoryLabel(" ")).toBe("이수구분 없음")
    expect(courseLine(course({ category: "", name: "과목", credit: 3, grade: "A0" }), "text")).toBe("과목 3학점 A0")
  })
})

describe("복사 글", () => {
  const a = course({ category: "전공필수", name: "자료구조", credit: 3, grade: "A+" })
  const b = course({ category: "교양선택", name: "미적분학1", credit: 3, grade: "B0" })

  it("표로 복사: 칸 사이 탭, 묶음 앞에 머리 줄", () => {
    expect(courseLine(a, "table")).toBe("전공필수\t자료구조\t3\tA+")
    expect(groupText([a, b], "table")).toBe("이수구분\t과목명\t학점\t성적\n전공필수\t자료구조\t3\tA+\n교양선택\t미적분학1\t3\tB0")
  })

  it("글로 복사: 한 줄에 과목명 (이수구분) N학점 성적", () => {
    expect(courseLine(a, "text")).toBe("자료구조 (전공필수) 3학점 A+")
    expect(groupText([a, b], "text")).toBe("자료구조 (전공필수) 3학점 A+\n미적분학1 (교양선택) 3학점 B0")
  })

  it("전체 복사: 묶음마다 [제목], 빈 줄로 나누고 빈 묶음은 뺀다", () => {
    const groups = [
      { title: "1학년 1학기", courses: [a] },
      { title: "1학년 2학기", courses: [] },
      { title: "2학년 1학기", courses: [b] },
    ]
    expect(allText(groups, "text")).toBe("[1학년 1학기]\n자료구조 (전공필수) 3학점 A+\n\n[2학년 1학기]\n미적분학1 (교양선택) 3학점 B0")
  })
})

describe("확인 필요 칸 고치기", () => {
  it("고친 값을 넣고, 성적은 맞추고, 숫자가 아닌 학점과 빈 성적은 읽은 값을 쓴다", () => {
    const list = [course({ credit: 3, grade: "B0" }), course({ credit: 2, grade: "A0" })]
    const edited = applyEdits(list, { 0: { credit: " 2 ", grade: "a" }, 1: { credit: "삼", grade: " " } })
    expect(edited[0]).toMatchObject({ credit: 2, grade: "A0" })
    expect(edited[1]).toMatchObject({ credit: 2, grade: "A0" })
    expect(applyEdits(list, { 0: { credit: "" } })[0].credit).toBe(3)
    expect(applyEdits(list, { 0: { credit: "-1" } })[0].credit).toBe(3)
    expect(applyEdits(list, { 0: { credit: "0.5" } })[0].credit).toBe(0.5)
    expect(applyEdits(list, { 0: { credit: "3,0" } })[0].credit).toBe(3)
  })

  it("parseCredit: 숫자와 쉼표 소수는 받고, 단위가 붙거나 빈 글은 null", () => {
    expect(parseCredit(" 2.5 ")).toBe(2.5)
    expect(parseCredit("3,0")).toBe(3)
    expect(parseCredit("0")).toBe(0)
    expect(parseCredit("3학점")).toBeNull()
    expect(parseCredit("")).toBeNull()
    expect(parseCredit("-1")).toBeNull()
  })

  it("고치지 않은 과목은 그대로 둔다", () => {
    const list = [course({})]
    expect(applyEdits(list, {})[0]).toBe(list[0])
  })

  it("노란 칸: 서버가 확실히 읽지 못한 칸과 고른 만점에서 매길 수 없는 성적", () => {
    expect(uncertainFields(course({ unsure: ["credit"] }), 4.5)).toEqual({ credit: true, grade: false })
    expect(uncertainFields(course({ grade: "A-" }), 4.5)).toEqual({ credit: false, grade: true })
    expect(uncertainFields(course({ grade: "A-" }), 4.3)).toEqual({ credit: false, grade: false })
    expect(uncertainCount([course({ unsure: ["credit", "grade"] }), course({ grade: "B-" }), course({ grade: "P" })], 4.5)).toBe(3)
  })
})

describe("transcriptFileProblem", () => {
  it("PDF와 사진만 받는다", () => {
    expect(transcriptFileProblem({ name: "성적.PDF", size: MB })).toBeNull()
    expect(transcriptFileProblem({ name: "성적.jpg", size: MB })).toBeNull()
    expect(transcriptFileProblem({ name: "성적.webp", size: MB })).toBeNull()
    expect(transcriptFileProblem({ name: "성적.docx", size: MB })).toMatch("만 올릴 수 있습니다")
    expect(transcriptFileProblem({ name: "성적.heic", size: MB })).toMatch("만 올릴 수 있습니다")
  })

  it("PDF는 10MB, 사진은 5MB까지", () => {
    expect(transcriptFileProblem({ name: "성적.pdf", size: 10 * MB })).toBeNull()
    expect(transcriptFileProblem({ name: "성적.pdf", size: 10 * MB + 1 })).toBe("PDF는 10MB까지 올릴 수 있습니다.")
    expect(transcriptFileProblem({ name: "성적.png", size: 5 * MB })).toBeNull()
    expect(transcriptFileProblem({ name: "성적.png", size: 5 * MB + 1 })).toBe("사진은 5MB까지 올릴 수 있습니다.")
  })
})

describe("시안의 예시 성적", () => {
  const { courses } = exampleTranscript()

  it("41과목, 8개 학기, 확인 필요 2칸", () => {
    expect(courses).toHaveLength(41)
    expect(semesterCount(courses)).toBe(8)
    expect(uncertainCount(courses, 4.5)).toBe(2)
  })

  it("재수강 전 성적을 빼면 114학점, 평점 3.87, 전공 3.89, 4.3 환산 3.70, 100점 86.0", () => {
    const counted = countedCourses(courses, true)
    const total = gradeStats(counted, 4.5)
    expect(total.credits).toBe(114)
    expect(formatGpa(total.gpa)).toBe("3.87")
    expect(formatGpa(gradeStats(counted.filter(isMajor), 4.5).gpa)).toBe("3.89")
    expect(convertGpa(total.gpa, 4.5, 4.3)).toBe("3.70")
    expect(toHundred(total.gpa, 4.5)).toBe("86.0")
  })

  it("재수강 전 성적을 넣으면 117학점, 평점 3.83", () => {
    const total = gradeStats(countedCourses(courses, false), 4.5)
    expect(total.credits).toBe(117)
    expect(formatGpa(total.gpa)).toBe("3.83")
  })
})
