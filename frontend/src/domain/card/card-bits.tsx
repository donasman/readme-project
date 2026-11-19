import type { ReactNode } from "react"

import { Dot } from "@/shared/components/dot"
import { subLabel } from "@/shared/components/heading"
import { CircleAlertIcon, CircleCheckIcon } from "@/shared/icons"

import { splitFlagged } from "./card-text"

// 카드 한 장을 그리는 조각. 카드 화면(상세, 확인, 목록)과 내 공고 상세의 카드 미리 보기 창이 함께 쓴다.

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

interface StoryPartProps {
  label: string
  text: string
  /** 칸 묶음의 클래스(간격, 칸 사이 가는 줄). 화면마다 줄의 자리가 다르다 */
  className: string
  /** 이름표의 제목 단계. 기본 h3 */
  heading?: "h3" | "h4"
  /** 빈 칸에 보일 것. 기본은 "아직 적지 않았습니다." */
  empty?: ReactNode
}

/** 이야기 한 칸(카드 상세, 카드 확인, 카드 미리 보기 창): 13px 회색 이름표와 글. 글 안의 확인 필요 표시를 강조한다. */
export function StoryPart({ label, text, className, heading: Heading = "h3", empty = <p className="text-sm text-text-secondary">아직 적지 않았습니다.</p> }: StoryPartProps) {
  return (
    <div className={className}>
      <Heading className={subLabel}>{label}</Heading>
      {text ? (
        <p>
          <FlaggedText text={text} />
        </p>
      ) : (
        empty
      )}
    </div>
  )
}

/** 근거 숫자 목록: 강조색 점과 글. */
export function FactList({ facts }: { facts: string[] }) {
  if (facts.length === 0) return <p className="text-sm text-text-secondary">아직 없습니다.</p>
  return (
    <ul className="flex flex-col gap-2">
      {facts.map((fact, i) => (
        <li key={i} className="flex items-start gap-2.5">
          <Dot tone="accent" className="mt-[9px]" />
          <span>{fact}</span>
        </li>
      ))}
    </ul>
  )
}
