// 카드 뽑기 결과의 "프로필 칸 확인"(12차 시안): 칸마다 지금 값과, 뽑기 전과 달라졌는지.
// 서버는 빈 칸만 채우므로 뽑기 전과 달라진 칸이 이번에 채운 칸이다. 목록은 통째로 본다.
import { formatCareerLength } from "@/domain/profile/format"
import type { Profile, ProfileInput } from "@/domain/profile/types"

export interface ProfileCheckRow {
  label: string
  /** 줄마다 값과 그 아래 작은 글. 비어 있으면 빈 칸이다 */
  lines: { value: string; note: string | null }[]
  /** 줄들 뒤의 작은 글(경력의 연차) */
  extra: string | null
  /** 이번 뽑기가 채웠다. 뽑기 전 프로필이 없으면(나갔다 와서 이어 본 경우) 늘 false */
  filled: boolean
}

const join = (parts: (string | false | null)[], separator = " · ") => parts.filter(Boolean).join(separator) || null

export function profileCheckRows(after: Profile, before: Profile | null): ProfileCheckRow[] {
  const row = (label: string, key: keyof ProfileInput, lines: ProfileCheckRow["lines"], extra: string | null = null): ProfileCheckRow => ({
    label,
    lines,
    extra,
    filled: lines.length > 0 && before !== null && JSON.stringify(before[key]) !== JSON.stringify(after[key]),
  })
  const one = (value: string | null) => (value ? [{ value, note: null }] : [])
  return [
    row("이름", "name", one(after.name)),
    row("생년월일", "birthDate", one(after.birthDate)),
    row(
      "학력",
      "educations",
      after.educations.map((e) => ({
        value: join([e.school, e.major], " ") ?? e.school,
        note: join([e.graduation && `${e.graduation} 졸업`, e.gpa !== null && `학점 ${e.gpa}${e.gpaScale !== null ? ` / ${e.gpaScale}` : ""}`]),
      }))
    ),
    row("자격증", "certificates", after.certificates.map((c) => ({ value: c.name, note: c.acquired && `${c.acquired} 취득` }))),
    row("어학", "languages", after.languages.map((l) => ({ value: `${l.test} ${l.score}`, note: l.taken && `${l.taken} 응시` }))),
    row(
      "경력",
      "careers",
      after.careers.map((k) => ({ value: join([k.company, k.role]) ?? k.company, note: k.end ? `${k.start} ~ ${k.end}` : `${k.start}부터 재직 중` })),
      after.careers.length > 0 ? formatCareerLength(after) : null
    ),
  ]
}
