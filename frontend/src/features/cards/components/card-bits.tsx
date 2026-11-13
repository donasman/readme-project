import type { ReactNode } from "react"

import { BackLink } from "@/shared/components/back-link"
import { CircleAlertIcon, CircleCheckIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"

import { splitFlagged } from "../card-text"

// 경험카드 화면들(목록, 상세, 고치기, 자료, 확인)이 함께 쓰는 조각. 시안: 시안 3종의 1차 "A · 카드 목록", 6차 "경험카드".

/** 카드 아래 화면들의 틀: 1120px 본문, 맨 위 "경험카드"로 돌아가는 링크. */
export function CardsSubpage({ children }: { children: ReactNode }) {
  return (
    <main className="mx-auto flex max-w-[1120px] flex-col gap-6 px-6 pt-6 pb-16">
      <BackLink to={paths.cards()}>경험카드</BackLink>
      {children}
    </main>
  )
}

/** 칸 이름(13px 굵은 회색): "상황", "태그", "근거 숫자" … */
export const subLabel = "text-label-sm font-bold text-text-secondary"

/** 칸 묶음 제목(18px 굵게): "STAR", "근거 숫자" … */
export const sectionTitle = "text-lg font-bold"

/** 켜고 끄는 outline 버튼(태그 거르기, 쓸 문항 유형)의 켜진 모양: 옅은 강조색 바탕. */
export const pressedOutline =
  "border-transparent bg-accent-subtle text-accent-strong hover:bg-accent-subtle hover:text-accent-strong dark:border-transparent dark:bg-accent-subtle dark:hover:bg-accent-subtle"

/** 확인 필요(하이라이트 알약) / 확인됨(초록 체크). */
export function CardStatusBadge({ verified }: { verified: boolean }) {
  return verified ? (
    <span className="inline-flex h-6 items-center gap-1 text-xs font-bold whitespace-nowrap text-text-secondary">
      <CircleCheckIcon aria-hidden="true" className="size-3.5 text-success" />
      <span>확인됨</span>
    </span>
  ) : (
    // 하이라이트색: 내가 할 일(확인)이 남은 카드
    <span className="inline-flex h-6 items-center gap-1 rounded-full bg-highlight pr-2.5 pl-1.5 text-xs font-bold whitespace-nowrap text-highlight-foreground">
      <CircleAlertIcon aria-hidden="true" className="size-3.5" />
      <span>확인 필요</span>
    </span>
  )
}

/** 글 안의 "[확인 필요: 30%]"를 하이라이트 표시로 그린다. */
export function FlaggedText({ text }: { text: string }) {
  return (
    <>
      {splitFlagged(text).map((part, i) =>
        part.flagged ? (
          <mark key={i} className="rounded-[6px] bg-highlight px-1.5 py-0.5 font-bold text-highlight-foreground">
            {part.text}
          </mark>
        ) : (
          <span key={i}>{part.text}</span>
        )
      )}
    </>
  )
}

/** 근거 숫자 목록: 강조색 점과 글. */
export function FactList({ facts }: { facts: string[] }) {
  if (facts.length === 0) return <p className="text-sm text-text-secondary">아직 없습니다.</p>
  return (
    <ul className="flex flex-col gap-2">
      {facts.map((fact) => (
        <li key={fact} className="flex items-start gap-2.5">
          <span aria-hidden="true" className="mt-[9px] size-1.5 flex-none rounded-full bg-primary" />
          <span>{fact}</span>
        </li>
      ))}
    </ul>
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
