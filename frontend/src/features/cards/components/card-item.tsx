import { CircleAlertIcon, CircleCheckIcon } from "@/shared/icons"

import { orgAndRole } from "../card-rules"
import type { ExperienceCard } from "../types"

/** 경험카드 한 장: 확인 상태와 기간, 제목과 어디서·맡은 일, 요약, 태그, 쓸 수 있는 문항 유형. */
export function CardItem({ card }: { card: ExperienceCard }) {
  const where = orgAndRole(card)
  return (
    <article className="flex flex-col gap-4 rounded-panel border border-border bg-surface p-6 shadow-resting">
      <div className="flex items-center justify-between gap-3">
        {card.verified ? (
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
        )}
        {card.period && <span className="text-xs whitespace-nowrap text-text-secondary">{card.period}</span>}
      </div>
      <div className="flex flex-col gap-1.5">
        <h2 className="text-lg font-bold">{card.title}</h2>
        {where && <p className="text-sm text-text-secondary">{where}</p>}
      </div>
      <p className="text-sm">{card.summary}</p>
      {card.tags.length > 0 && (
        <ul aria-label="태그" className="flex flex-wrap gap-1.5">
          {card.tags.map((tag) => (
            <li key={tag} className="rounded-full bg-surface-muted px-2.5 py-1 text-xs font-medium">
              {tag}
            </li>
          ))}
        </ul>
      )}
      <p className="mt-auto border-t border-border-subtle pt-4 text-xs text-text-secondary">
        <span className="sr-only">쓸 수 있는 문항 유형: </span>
        {card.usableFor.join(" · ")}
      </p>
    </article>
  )
}
