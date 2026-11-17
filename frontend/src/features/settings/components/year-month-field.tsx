import { seoulToday } from "@/shared/lib/date"
import { joinClasses } from "@/shared/lib/utils"

import type { MonthDraft } from "../profile-form"

const MONTHS = Array.from({ length: 12 }, (_, i) => String(i + 1).padStart(2, "0"))
const OLDEST_YEAR = 1950
// 졸업 예정 년월까지 고를 수 있게 올해보다 몇 해 뒤까지 둔다
const YEARS_AHEAD = 6

/** 고를 연도: 몇 해 뒤부터 1950년까지 내림차순. 그 밖의 값(자료에서 읽은 옛 연도)이 들어 있으면 그 해도 넣는다. */
function yearOptions(current: string): string[] {
  const thisYear = Number(seoulToday().slice(0, 4))
  const years = Array.from({ length: thisYear + YEARS_AHEAD - OLDEST_YEAR + 1 }, (_, i) => String(thisYear + YEARS_AHEAD - i))
  return current && !years.includes(current) ? [...years, current].sort().reverse() : years
}

const select =
  "h-control-md min-w-0 rounded-full border border-input bg-transparent pr-2.5 pl-3.5 text-base outline-none focus-visible:border-ring disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 dark:bg-input/30"

interface YearMonthFieldProps {
  /** 연도 칸의 id. 이름표는 `${id}-label`이다 */
  id: string
  label: string
  value: MonthDraft
  onChange: (value: MonthDraft) => void
  disabled?: boolean
  invalid?: boolean
  describedBy?: string
  className?: string
}

/** 년월 칸(12차 시안): 연도와 월 고르기 두 개. type=month는 Safari·Firefox에 달 고르기가 없어 쓰지 않는다. */
export function YearMonthField({ id, label, value, onChange, disabled, invalid, describedBy, className }: YearMonthFieldProps) {
  const common = { disabled, "aria-invalid": invalid || undefined, "aria-describedby": describedBy }
  const tone = (picked: string) => (picked ? "text-foreground" : "text-text-muted")
  return (
    <div role="group" aria-labelledby={`${id}-label`} className={joinClasses("flex min-w-0 flex-col gap-1.5", className)}>
      <span id={`${id}-label`} className="text-sm font-medium">
        {label}
      </span>
      <div className="flex gap-2">
        <select id={id} aria-label="연도" value={value.year} onChange={(e) => onChange({ ...value, year: e.target.value })} className={joinClasses(select, "flex-[5_1_0]", tone(value.year))} {...common}>
          <option value="">연도</option>
          {yearOptions(value.year).map((year) => (
            <option key={year} value={year}>
              {year}년
            </option>
          ))}
        </select>
        <select aria-label="월" value={value.month} onChange={(e) => onChange({ ...value, month: e.target.value })} className={joinClasses(select, "flex-[4_1_0]", tone(value.month))} {...common}>
          <option value="">월</option>
          {MONTHS.map((month) => (
            <option key={month} value={month}>
              {Number(month)}월
            </option>
          ))}
        </select>
      </div>
    </div>
  )
}
