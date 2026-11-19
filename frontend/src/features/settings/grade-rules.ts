// 학점 계산기의 규칙(학점 계산기 문서 3절). 서버는 읽은 과목 줄만 주고, 합계·환산·묶기·복사 글은 화면이 여기서 낸다. 순수 함수.
import { IMAGE_ACCEPT, MAX_IMAGE_BYTES } from "@/shared/lib/api"
import { matchesAccept } from "@/shared/lib/utils"

import type { GradeScale, TranscriptCourse } from "./types"

/** 고를 수 있는 만점. 앞이 기본이다(GC4). */
export const SCALES: GradeScale[] = [4.5, 4.3]

/** 등급별 평점. 표에 없는 등급(4.5 만점의 A- 등)은 평점에서 빠진다(GC5). */
const POINTS: Record<GradeScale, Record<string, number>> = {
  4.5: { "A+": 4.5, A0: 4.0, "B+": 3.5, B0: 3.0, "C+": 2.5, C0: 2.0, "D+": 1.5, D0: 1.0, F: 0 },
  4.3: { "A+": 4.3, A0: 4.0, "A-": 3.7, "B+": 3.3, B0: 3.0, "B-": 2.7, "C+": 2.3, C0: 2.0, "C-": 1.7, "D+": 1.3, D0: 1.0, "D-": 0.7, F: 0 },
}

/** P/F 과목의 등급. P, S는 취득학점에만 들고 NP, U는 어디에도 들지 않는다(GC6). */
const PASS_FAIL = new Set(["P", "S", "NP", "U"])

/** 취득학점에 들지 않는 등급. F는 평점에는 0으로 든다. */
const NOT_EARNED = new Set(["F", "NP", "U"])

/** 등급의 평점. 고른 만점의 표에 없으면 undefined. */
export const gradePoint = (grade: string, scale: GradeScale): number | undefined => POINTS[scale][grade]

/** 확인할 등급: 고른 만점의 표에도 P/F에도 없다(읽지 못했거나 4.5 만점의 A- 등, GC5). */
export const needsGradeCheck = (grade: string, scale: GradeScale): boolean => gradePoint(grade, scale) === undefined && !PASS_FAIL.has(grade)

/** 고쳐 넣은 성적: 대문자로 바꾸고, 기호가 없으면(A, Ao) 0을 붙인다. */
export function normalizeGrade(text: string): string {
  const grade = text.trim().toUpperCase()
  return /^[A-D]O?$/.test(grade) ? `${grade[0]}0` : grade
}

export interface GradeStats {
  /** 취득학점: F, NP, U가 아닌 과목의 학점 합 */
  credits: number
  /** 평점평균: 표에 있는 등급의 과목만 센다. 그런 과목이 없으면 null */
  gpa: number | null
}

export function gradeStats(courses: Pick<TranscriptCourse, "credit" | "grade">[], scale: GradeScale): GradeStats {
  let credits = 0
  let graded = 0
  let points = 0
  for (const { credit, grade } of courses) {
    if (!NOT_EARNED.has(grade)) credits += credit
    const point = gradePoint(grade, scale)
    if (point === undefined) continue
    graded += credit
    points += point * credit
  }
  return { credits, gpa: graded ? points / graded : null }
}

/** 전공 과목: 이수구분이 "전공"으로 시작한다. */
export const isMajor = (course: Pick<TranscriptCourse, "category">) => course.category.startsWith("전공")

/** 합계와 복사에 넣을 과목: "재수강 전 성적 빼기"가 켜져 있으면 재수강하기 전의 줄을 뺀다. */
export const countedCourses = <T extends Pick<TranscriptCourse, "retake">>(courses: T[], dropRetake: boolean): T[] =>
  dropRetake ? courses.filter((c) => c.retake !== "before") : courses

// 소수점 아래를 반올림하지 않고 버린다. 3.57이 3.5699…로 계산되어도 3.56이 되지 않게 아주 작은 값을 더한다
const floorTo = (value: number, digits: number) => (Math.floor(value * 10 ** digits + 1e-9) / 10 ** digits).toFixed(digits)

/** 평점: 소수 둘째 자리까지, 그 아래는 버린다. 없으면 "-". */
export const formatGpa = (gpa: number | null): string => (gpa === null ? "-" : floorTo(gpa, 2))

/** 다른 만점으로 단순 비례 환산한 평점(GC7). */
export const convertGpa = (gpa: number | null, from: GradeScale, to: GradeScale): string => (gpa === null ? "-" : floorTo((gpa / from) * to, 2))

/** 100점 환산: 소수 첫째 자리까지, 그 아래는 버린다(GC7). */
export const toHundred = (gpa: number | null, scale: GradeScale): string => (gpa === null ? "-" : floorTo((gpa / scale) * 100, 1))

/** 학기 수: 학년과 학기(계절학기 포함)가 다른 것의 수. */
export const semesterCount = (courses: Pick<TranscriptCourse, "year" | "term">[]) => new Set(courses.map((c) => `${c.year}-${c.term}`)).size

/** 묶는 방식: 학기별, 학년별, 이수구분별. */
export type GroupBy = "term" | "year" | "category"

export interface CourseGroup<T> {
  key: string
  title: string
  courses: T[]
}

const TERM_ORDER = ["1", "여름", "2", "겨울"]
const CATEGORY_ORDER = ["전공필수", "전공선택", "교양필수", "교양선택", "일반선택"]
// 목록에 없는 값은 뒤로 보내고, 같은 순위끼리는 나온 순서를 지킨다(sort는 안정 정렬이다)
const rank = (order: string[], value: string) => (order.includes(value) ? order.indexOf(value) : order.length)

/** 과목을 묶는다. 학기별·학년별은 학년과 학기 순서, 이수구분별은 전공필수·전공선택·교양필수·교양선택·일반선택 다음 나머지. */
export function groupCourses<T extends Pick<TranscriptCourse, "year" | "term" | "category">>(courses: T[], by: GroupBy): CourseGroup<T>[] {
  const sorted = [...courses].sort((a, b) =>
    by === "category" ? rank(CATEGORY_ORDER, a.category) - rank(CATEGORY_ORDER, b.category) : a.year - b.year || (by === "term" ? rank(TERM_ORDER, a.term) - rank(TERM_ORDER, b.term) : 0)
  )
  const groups = new Map<string, CourseGroup<T>>()
  for (const course of sorted) {
    const key = by === "term" ? `${course.year}-${course.term}` : by === "year" ? String(course.year) : course.category
    const title = by === "term" ? `${course.year}학년 ${course.term}학기` : by === "year" ? `${course.year}학년` : course.category
    const group = groups.get(key) ?? { key, title, courses: [] }
    group.courses.push(course)
    groups.set(key, group)
  }
  return [...groups.values()]
}

/** 복사 형식: 표(칸 사이 탭), 글(한 줄에 과목 하나). */
export type CopyFormat = "table" | "text"

type CopyCourse = Pick<TranscriptCourse, "category" | "name" | "credit" | "grade">

/** 과목 한 줄의 복사 글. */
export const courseLine = (c: CopyCourse, format: CopyFormat): string =>
  format === "table" ? [c.category, c.name, c.credit, c.grade].join("\t") : `${c.name} (${c.category}) ${c.credit}학점 ${c.grade}`

/** 묶음 하나의 복사 글. 표는 머리 줄(이수구분, 과목명, 학점, 성적)을 앞에 둔다. */
export const groupText = (courses: CopyCourse[], format: CopyFormat): string =>
  [...(format === "table" ? ["이수구분\t과목명\t학점\t성적"] : []), ...courses.map((c) => courseLine(c, format))].join("\n")

/** 전체 복사 글: 묶음마다 [제목]과 그 묶음의 글을 빈 줄로 나눈다. 넣을 과목이 없는 묶음은 뺀다. */
export const allText = (groups: { title: string; courses: CopyCourse[] }[], format: CopyFormat): string =>
  groups
    .filter((g) => g.courses.length > 0)
    .map((g) => `[${g.title}]\n${groupText(g.courses, format)}`)
    .join("\n\n")

/** 확인 필요 칸에 고쳐 넣은 글. 과목의 순서(index)로 찾는다. */
export interface CourseEdit {
  credit?: string
  grade?: string
}

/** 고친 값을 과목에 넣는다. 숫자가 아닌 학점과 빈 성적은 넣지 않고 읽은 값을 쓴다. */
export function applyEdits<T extends TranscriptCourse>(courses: T[], edits: Record<number, CourseEdit>): T[] {
  return courses.map((course, i) => {
    const edit = edits[i]
    if (!edit) return course
    const credit = edit.credit?.trim() ? Number(edit.credit.trim()) : NaN
    return {
      ...course,
      credit: Number.isFinite(credit) && credit >= 0 ? credit : course.credit,
      grade: edit.grade?.trim() ? normalizeGrade(edit.grade) : course.grade,
    }
  })
}

/** 노란 입력 칸으로 보일 칸: 서버가 확실히 읽지 못한 칸과, 고른 만점에서 평점을 매길 수 없는 성적. 읽은 값으로 정해 고치는 동안 칸이 바뀌지 않는다. */
export function uncertainFields(course: TranscriptCourse, scale: GradeScale): { credit: boolean; grade: boolean } {
  return { credit: course.unsure.includes("credit"), grade: course.unsure.includes("grade") || needsGradeCheck(course.grade, scale) }
}

/** 확인 필요 칸 수. */
export const uncertainCount = (courses: TranscriptCourse[], scale: GradeScale): number =>
  courses.reduce((sum, c) => {
    const { credit, grade } = uncertainFields(c, scale)
    return sum + Number(credit) + Number(grade)
  }, 0)

const PDF_MAX_BYTES = 10 * 1024 * 1024

/** 고르기 창의 accept: PDF와 사진(GC3). */
export const TRANSCRIPT_ACCEPT = `.pdf,${IMAGE_ACCEPT}`

/** 성적증명서를 보내지 못하는 이유. 보낼 수 있으면 null. 큰 파일을 다 보낸 뒤 서버가 거절하지 않게 먼저 본다(GC3). */
export function transcriptFileProblem(file: { name: string; size: number }): string | null {
  if (!matchesAccept(file.name, TRANSCRIPT_ACCEPT)) return "PDF와 사진(JPG, PNG, GIF, WebP)만 올릴 수 있습니다."
  if (matchesAccept(file.name, IMAGE_ACCEPT)) return file.size > MAX_IMAGE_BYTES ? "사진은 5MB까지 올릴 수 있습니다." : null
  return file.size > PDF_MAX_BYTES ? "PDF는 10MB까지 올릴 수 있습니다." : null
}
