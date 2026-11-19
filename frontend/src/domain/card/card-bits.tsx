import { Dot } from "@/shared/components/dot"
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
