// 기본 프로필(GET·PUT /api/me/profile, 프로필 설계 5절). 이력서에 적는 기본 정보로 나만 본다. 년월은 "YYYY-MM"이다.

export type Gender = "male" | "female"

export interface Education {
  school: string
  major: string | null
  /** 졸업(예정) 년월 */
  graduation: string | null
  gpa: number | null
  gpaScale: number | null
}

export interface Certificate {
  name: string
  acquired: string | null
}

export interface Language {
  test: string
  score: string
  taken: string | null
}

/** 경력. end가 null이면 재직 중이다. */
export interface Career {
  company: string
  role: string | null
  start: string
  end: string | null
}

/** 저장할 때 보내는 것. PUT은 전체를 갈아 끼운다. */
export interface ProfileInput {
  name: string | null
  birthDate: string | null
  gender: Gender | null
  educations: Education[]
  certificates: Certificate[]
  languages: Language[]
  careers: Career[]
}

/** 서버가 센 경력 달 수와 연차가 붙는다(겹친 달은 한 번, 재직 중이면 이번 달까지). */
export interface Profile extends ProfileInput {
  careerMonths: number
  careerYears: number
}

/** 학력·자격증·어학·경력은 저마다 20줄까지 받는다. */
export const PROFILE_LIST_MAX = 20
