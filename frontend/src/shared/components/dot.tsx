import { joinClasses } from "@/shared/lib/utils"

const TONES = {
  accent: "bg-primary",
  muted: "bg-border-strong",
  danger: "bg-danger",
} as const

/** 목록 앞의 6px 점. 첫 줄 가운데에 맞추는 위 여백(mt-…)은 글자 크기에 따라 부르는 쪽이 준다. */
export function Dot({ tone, className }: { tone: keyof typeof TONES; className?: string }) {
  return <span aria-hidden="true" className={joinClasses("size-1.5 flex-none rounded-full", TONES[tone], className)} />
}
