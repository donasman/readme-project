// 기본 프로필 화면의 입력 상태와 저장 전 검사. 서버(ProfileRequests)와 같은 규칙을 먼저 보아 틀린 칸을 한꺼번에 표시한다.
import type { Profile, ProfileInput } from "@/domain/profile/types"

/** 년월 칸: 연도("2024")와 월("03")을 따로 고른다. 빈 글자는 고르지 않은 것 */
export interface MonthDraft {
  year: string
  month: string
}

export interface EducationDraft {
  key: number
  school: string
  major: string
  graduation: MonthDraft
  gpa: string
  gpaScale: string
}

export interface CertificateDraft {
  key: number
  name: string
  acquired: MonthDraft
}

export interface LanguageDraft {
  key: number
  test: string
  score: string
  taken: MonthDraft
}

export interface CareerDraft {
  key: number
  company: string
  role: string
  start: MonthDraft
  end: MonthDraft
  /** 켜면 끝 년월을 보내지 않는다(서버는 끝 달이 비면 재직 중으로 본다) */
  working: boolean
}

export interface ProfileDraft {
  name: string
  birthDate: string
  educations: EducationDraft[]
  certificates: CertificateDraft[]
  languages: LanguageDraft[]
  careers: CareerDraft[]
}

/** 칸의 id(화면의 입력 id)마다 고칠 글. */
export type FieldErrors = Record<string, string>

let lastKey = 0
const newKey = () => ++lastKey

const NO_MONTH: MonthDraft = { year: "", month: "" }
const toMonth = (value: string | null): MonthDraft => (value ? { year: value.slice(0, 4), month: value.slice(5, 7) } : NO_MONTH)

export const newEducation = (): EducationDraft => ({ key: newKey(), school: "", major: "", graduation: NO_MONTH, gpa: "", gpaScale: "" })
export const newCertificate = (): CertificateDraft => ({ key: newKey(), name: "", acquired: NO_MONTH })
export const newLanguage = (): LanguageDraft => ({ key: newKey(), test: "", score: "", taken: NO_MONTH })
export const newCareer = (): CareerDraft => ({ key: newKey(), company: "", role: "", start: NO_MONTH, end: NO_MONTH, working: false })

export function toDraft(profile: Profile): ProfileDraft {
  return {
    name: profile.name ?? "",
    birthDate: profile.birthDate ?? "",
    educations: profile.educations.map((e) => ({
      ...newEducation(),
      school: e.school,
      major: e.major ?? "",
      graduation: toMonth(e.graduation),
      gpa: e.gpa?.toString() ?? "",
      gpaScale: e.gpaScale?.toString() ?? "",
    })),
    certificates: profile.certificates.map((c) => ({ ...newCertificate(), name: c.name, acquired: toMonth(c.acquired) })),
    languages: profile.languages.map((l) => ({ ...newLanguage(), test: l.test, score: l.score, taken: toMonth(l.taken) })),
    careers: profile.careers.map((k) => ({ ...newCareer(), company: k.company, role: k.role ?? "", start: toMonth(k.start), end: toMonth(k.end), working: k.end === null })),
  }
}

/** 아무것도 없는 프로필(처음 들어온 사람). */
export const isEmptyProfile = (p: Profile): boolean =>
  !p.name && !p.birthDate && p.educations.length + p.certificates.length + p.languages.length + p.careers.length === 0

/** 입력 id. 화면과 검사가 같은 이름을 쓴다. */
export const fieldId = (section: "edu" | "cert" | "lang" | "career", key: number, field: string) => `pf-${section}-${key}-${field}`

// 학점과 만점: 서버는 정수 3자리, 소수 2자리까지 받는다
const DECIMAL = /^\d{1,3}(\.\d{1,2})?$/
const OLDEST_BIRTH_DATE = "1900-01-01"

const blank = (...values: string[]) => values.every((v) => !v.trim())
const noMonth = (m: MonthDraft) => !m.year && !m.month
const orNull = (value: string) => value.trim() || null

/** 저장 전 검사. 통과하면 보낼 값을, 아니면 칸마다 고칠 글을 준다. 아무것도 넣지 않은 줄은 버린다. */
export function checkDraft(draft: ProfileDraft, today: string): { input: ProfileInput; errors: null } | { input: null; errors: FieldErrors } {
  const errors: FieldErrors = {}
  const month = (m: MonthDraft, id: string): string | null => {
    if (noMonth(m)) return null
    if (m.year && m.month) return `${m.year}-${m.month}`
    errors[id] = "연도와 월을 함께 골라 주세요."
    return null
  }
  const required = (value: string, id: string, message: string) => {
    if (!value.trim()) errors[id] = message
    return value.trim()
  }

  if (draft.birthDate && (draft.birthDate > today || draft.birthDate < OLDEST_BIRTH_DATE)) errors["pf-birth"] = "생년월일을 확인해 주세요."

  const educations = draft.educations
    .filter((e) => !(blank(e.school, e.major, e.gpa, e.gpaScale) && noMonth(e.graduation)))
    .map((e) => {
      const id = (field: string) => fieldId("edu", e.key, field)
      const gpa = e.gpa.trim()
      const scale = e.gpaScale.trim()
      if (gpa && !DECIMAL.test(gpa)) errors[id("gpa")] = "학점은 숫자로 넣어 주세요. 예: 3.8"
      if (scale && (!DECIMAL.test(scale) || Number(scale) === 0)) errors[id("scale")] = "만점은 0보다 큰 숫자로 넣어 주세요. 예: 4.5"
      if (!errors[id("gpa")] && !errors[id("scale")] && gpa && scale && Number(gpa) > Number(scale)) errors[id("gpa")] = "학점이 만점보다 큽니다."
      return {
        school: required(e.school, id("school"), "학교를 넣어 주세요."),
        major: orNull(e.major),
        graduation: month(e.graduation, id("graduation")),
        gpa: gpa ? Number(gpa) : null,
        gpaScale: scale ? Number(scale) : null,
      }
    })

  const certificates = draft.certificates
    .filter((c) => !(blank(c.name) && noMonth(c.acquired)))
    .map((c) => ({
      name: required(c.name, fieldId("cert", c.key, "name"), "자격증 이름을 넣어 주세요."),
      acquired: month(c.acquired, fieldId("cert", c.key, "acquired")),
    }))

  const languages = draft.languages
    .filter((l) => !(blank(l.test, l.score) && noMonth(l.taken)))
    .map((l) => ({
      test: required(l.test, fieldId("lang", l.key, "test"), "시험 이름을 넣어 주세요."),
      score: required(l.score, fieldId("lang", l.key, "score"), "점수를 넣어 주세요."),
      taken: month(l.taken, fieldId("lang", l.key, "taken")),
    }))

  const careers = draft.careers
    .filter((k) => !(blank(k.company, k.role) && noMonth(k.start) && noMonth(k.end)))
    .map((k) => {
      const id = (field: string) => fieldId("career", k.key, field)
      const start = month(k.start, id("start"))
      if (noMonth(k.start)) errors[id("start")] = "시작 년월을 골라 주세요."
      let end: string | null = null
      if (!k.working) {
        end = month(k.end, id("end"))
        if (noMonth(k.end)) errors[id("end")] = "끝 년월을 고르거나 재직 중을 표시해 주세요."
        else if (start && end && end < start) errors[id("end")] = "경력의 끝 달이 시작 달보다 앞입니다."
      }
      return { company: required(k.company, id("company"), "회사를 넣어 주세요."), role: orNull(k.role), start: start ?? "", end }
    })

  if (Object.keys(errors).length > 0) return { input: null, errors }
  return {
    input: { name: orNull(draft.name), birthDate: draft.birthDate || null, educations, certificates, languages, careers },
    errors: null,
  }
}

/** 서버가 대표 표기로 바꾼 자격증 이름("컴활 → 컴퓨터활용능력"). 저장은 보낸 순서를 지킨다. */
export const certificateRenames = (sent: ProfileInput, saved: Profile): string[] =>
  sent.certificates.flatMap((c, i) => (saved.certificates[i] && saved.certificates[i].name !== c.name ? [`${c.name} → ${saved.certificates[i].name}`] : []))
