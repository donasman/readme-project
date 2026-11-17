// 가짜 서버의 기본 프로필(GET·PUT /api/me/profile). 12차 시안의 값이다. ?mock=empty-profile이면 빈 프로필로 시작한다.
import type { Career, Profile, ProfileInput } from "@/domain/profile/types"

import { mockFlag, today } from "./db"

let stored: ProfileInput | null = null

function initial(): ProfileInput {
  if (mockFlag("empty-profile")) return { name: null, birthDate: null, gender: null, educations: [], certificates: [], languages: [], careers: [] }
  return {
    name: "김다온",
    birthDate: "1999-03-01",
    gender: "female",
    educations: [{ school: "한국대학교", major: "컴퓨터공학", graduation: "2024-02", gpa: 3.8, gpaScale: 4.5 }],
    certificates: [
      { name: "정보처리기사", acquired: "2023-11" },
      { name: "컴퓨터활용능력", acquired: "2022-08" },
    ],
    languages: [{ test: "TOEIC", score: "900", taken: "2025-01" }],
    careers: [{ company: "가나다", role: "백엔드 개발", start: "2024-03", end: null }],
  }
}

// 서버 사전의 대표 표기 가운데 시안에 나온 것
const CERTIFICATE_NAMES = new Map([
  ["컴활", "컴퓨터활용능력"],
  ["정처기", "정보처리기사"],
])

const nextMonth = (month: string): string => {
  const [year, m] = month.split("-").map(Number)
  return m === 12 ? `${year + 1}-01` : `${year}-${String(m + 1).padStart(2, "0")}`
}

/** 경력 달 수: 시작과 끝 달을 넣고, 겹친 달은 한 번, 재직 중이면 이번 달까지 센다(서버의 CareerMonths). */
function careerMonths(careers: Career[]): number {
  const now = today().slice(0, 7)
  const months = new Set<string>()
  for (const career of careers) for (let m = career.start; m <= (career.end ?? now); m = nextMonth(m)) months.add(m)
  return months.size
}

export function getProfile(): Profile {
  const profile = structuredClone((stored ??= initial()))
  const months = careerMonths(profile.careers)
  return { ...profile, careerMonths: months, careerYears: Math.floor(months / 12) }
}

/** 저장. 서버처럼 자격증 이름을 대표 표기로 바꾼다. */
export function saveProfile(input: ProfileInput): Profile {
  stored = { ...structuredClone(input), certificates: input.certificates.map((c) => ({ ...c, name: CERTIFICATE_NAMES.get(c.name) ?? c.name })) }
  return getProfile()
}
