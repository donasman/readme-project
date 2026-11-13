import { useId } from "react"

import { joinClasses } from "@/shared/lib/utils"

import { DISCLOSURE_DESCRIPTIONS, DISCLOSURE_LABELS, DISCLOSURES } from "./disclosure"
import type { Disclosure } from "./types"

interface DisclosureOptionsProps {
  value: Disclosure
  onChange: (value: Disclosure) => void
  /** 묶음 이름을 단 요소의 id(제목) */
  labelledBy: string
}

/** 공개 범위 셋 중 하나를 고르는 칸들. 멤버 화면과 "공개 범위" 창이 함께 쓴다. 고른 칸은 강조색 테두리와 옅은 바탕. */
export function DisclosureOptions({ value, onChange, labelledBy }: DisclosureOptionsProps) {
  const name = useId()
  return (
    <div role="radiogroup" aria-labelledby={labelledBy} className="flex flex-col gap-2">
      {DISCLOSURES.map((option) => {
        const on = option === value
        return (
          <label
            key={option}
            className={joinClasses(
              "flex cursor-pointer items-start gap-3 rounded-control border px-4 py-3.5",
              on ? "border-primary bg-accent-subtle" : "border-border bg-transparent"
            )}
          >
            <input
              type="radio"
              name={name}
              checked={on}
              onChange={() => onChange(option)}
              className="mt-0.5 size-[18px] flex-none accent-primary"
            />
            <span className="flex min-w-0 flex-col gap-0.5">
              <span className="text-sm font-bold">{DISCLOSURE_LABELS[option]}</span>
              <span className="text-label-sm text-text-secondary">{DISCLOSURE_DESCRIPTIONS[option]}</span>
            </span>
          </label>
        )
      })}
    </div>
  )
}
