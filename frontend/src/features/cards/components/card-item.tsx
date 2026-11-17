import { Link } from "react-router"

import { Chip } from "@/shared/components/chip"
import { sectionTitle } from "@/shared/components/heading"
import { Panel } from "@/shared/components/panel"
import { paths } from "@/shared/lib/paths"

import { orgAndRole } from "../card-rules"
import type { ExperienceCard } from "../types"
import { CardStatusBadge } from "./card-bits"

/**
 * 경험카드 한 장: 확인 상태와 기간, 제목과 어디서·맡은 일, 요약, 태그, 쓸 수 있는 문항 유형. 카드 전체가 상세로 가는 링크다.
 * 경험 묶음 안에 있으면 묶음 제목(h2) 아래라 제목이 h3다.
 */
export function CardItem({ card, level = 2 }: { card: ExperienceCard; level?: 2 | 3 }) {
  const where = orgAndRole(card)
  const Title = level === 2 ? "h2" : "h3"
  return (
    <Panel as="article" className="relative flex flex-col gap-4 p-6">
      <div className="flex items-center justify-between gap-3">
        <CardStatusBadge verified={card.verified} />
        {card.period && <span className="text-xs whitespace-nowrap text-text-secondary">{card.period}</span>}
      </div>
      <div className="flex flex-col gap-1.5">
        <Title className={sectionTitle}>
          {/* 카드 어디를 눌러도 상세로 간다(링크의 ::after가 카드를 덮는다) */}
          <Link to={paths.card(card.id)} className="after:absolute after:inset-0 after:rounded-panel">
            {card.title}
          </Link>
        </Title>
        {where && <p className="text-sm text-text-secondary">{where}</p>}
      </div>
      <p className="text-sm">{card.summary}</p>
      {card.tags.length > 0 && (
        <ul aria-label="태그" className="flex flex-wrap gap-1.5">
          {card.tags.map((tag) => (
            <Chip key={tag}>{tag}</Chip>
          ))}
        </ul>
      )}
      <p className="mt-auto border-t border-border-subtle pt-4 text-xs text-text-secondary">
        <span className="sr-only">쓸 수 있는 문항 유형: </span>
        {card.usableFor.join(" · ")}
      </p>
    </Panel>
  )
}
