import type { ReactNode } from "react"

import { BackLink } from "@/shared/components/back-link"
import { Page } from "@/shared/components/page"
import { paths } from "@/shared/lib/paths"

// 경험카드 화면들(목록, 상세, 고치기, 자료, 확인)이 함께 쓰는 조각. 시안: 시안 3종의 1차 "A · 카드 목록", 6차 "경험카드".
// 카드 한 장을 그리는 조각(확인 표시, 확인 필요 글, 근거 숫자)은 domain/card/card-bits.tsx에 있다.

/** 카드 아래 화면들의 틀: 1120px 본문, 맨 위 "경험카드"로 돌아가는 링크. */
export function CardsSubpage({ children }: { children: ReactNode }) {
  return (
    <Page narrow className="gap-6 pt-6">
      <BackLink to={paths.cards()}>경험카드</BackLink>
      {children}
    </Page>
  )
}

/** 숫자 한 칸: 이름(13px)과 큰 숫자(22px). dl 안에 둔다. */
export function StatBox({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-1 rounded-control border border-border p-4">
      <dt className="text-label-sm text-text-secondary">{label}</dt>
      <dd className="text-[22px] leading-[1.1] font-bold">{value}</dd>
    </div>
  )
}

/** 진행 막대(8px). value/max 만큼 강조색으로 채운다. */
export function ProgressBar({ label, value, max }: { label: string; value: number; max: number }) {
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={max}
      aria-valuenow={value}
      className="h-2 overflow-hidden rounded-full bg-control-track"
    >
      <div className="h-full rounded-full bg-primary" style={{ width: `${max ? (value / max) * 100 : 0}%` }} />
    </div>
  )
}
