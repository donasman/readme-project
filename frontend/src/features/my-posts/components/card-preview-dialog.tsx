import { useEffect, useState } from "react"

import { Button } from "@/components/ui/button"
import { getCard } from "@/domain/card/api"
import { CardStatusBadge, FactList, FlaggedText } from "@/domain/card/card-bits"
import { STORY_PARTS } from "@/domain/card/card-rules"
import type { CardDetail } from "@/domain/card/types"
import { AppDialog, DialogCancel } from "@/shared/components/app-dialog"
import { ArrowLink } from "@/shared/components/arrow-link"
import { Chip } from "@/shared/components/chip"
import { subLabel } from "@/shared/components/heading"
import { ChevronLeftIcon, ChevronRightIcon } from "@/shared/icons"
import { errorMessage } from "@/shared/lib/api"
import { paths } from "@/shared/lib/paths"

import { cardPosition, stepCard } from "../requirement-cards"
import type { CardMatch, RequirementResult } from "../types"

/** 창에 띄운 것: 어느 요건의 몇 번째 카드인지 */
export interface CardPreview {
  requirement: RequirementResult
  index: number
}

interface CardPreviewDialogProps {
  preview: CardPreview | null
  /** 맞는 카드(제목과 경험 이름). 카드를 읽기 전에도 제목을 보인다 */
  matches: Map<string, CardMatch>
  onIndex: (index: number) => void
  onClose: () => void
}

/** 묶음 사이의 가는 줄 */
const divided = "flex flex-col gap-2 border-t border-border-subtle pt-5"

/**
 * 카드 미리 보기 창(최종 시안 "요건과 내 카드"의 카드 창): 요건 글과 자리, 경험 이름, 확인 필요, 제목, 기간·소속·역할,
 * 이 요건과 겹친 태그, 요약, 이야기, 근거 숫자, 태그. 카드 내용은 창을 열거나 넘길 때 GET /api/cards/{cardId}로 읽는다(공고 문서 4.8).
 */
export function CardPreviewDialog({ preview, matches, onIndex, onClose }: CardPreviewDialogProps) {
  const target = preview?.requirement.cards[preview.index]
  const cardId = target?.cardId
  const [loaded, setLoaded] = useState<{ id: string; card: CardDetail } | { id: string; error: string } | null>(null)

  useEffect(() => {
    if (!cardId) return
    let live = true
    getCard(cardId).then(
      (card) => live && setLoaded({ id: cardId, card }),
      (error: unknown) => live && setLoaded({ id: cardId, error: errorMessage(error) })
    )
    return () => {
      live = false
    }
  }, [cardId])

  if (!preview || !target) return <AppDialog open={false} onOpenChange={onClose} title="" actions={null} />

  const { requirement, index } = preview
  const count = requirement.cards.length
  const prev = stepCard(index, -1, count)
  const next = stepCard(index, 1, count)
  const current = loaded?.id === target.cardId ? loaded : null
  const card = current && "card" in current ? current.card : null
  const match = matches.get(target.cardId)
  const experience = card?.experience ?? match?.experience ?? ""
  const where = card ? [card.period, card.org, card.role].filter(Boolean).join(" · ") : ""

  return (
    <AppDialog
      open
      onOpenChange={(open) => !open && onClose()}
      wide
      title={card?.title ?? match?.title ?? ""}
      subtitle={where || undefined}
      eyebrow={
        <div className="flex flex-col gap-3">
          <div className="flex items-center justify-between gap-2">
            <p role="status" className="min-w-0 text-label-sm text-text-secondary">
              <strong className="font-bold text-foreground">{requirement.text}</strong> · {cardPosition(index, count)}
            </p>
            <div className="-my-2 flex flex-none items-center">
              <Button type="button" variant="ghost" size="icon" aria-label="이전 카드" disabled={prev === null} onClick={() => prev !== null && onIndex(prev)}>
                <ChevronLeftIcon aria-hidden="true" />
              </Button>
              <Button type="button" variant="ghost" size="icon" aria-label="다음 카드" disabled={next === null} onClick={() => next !== null && onIndex(next)}>
                <ChevronRightIcon aria-hidden="true" />
              </Button>
            </div>
          </div>
          {(experience || (card && !card.verified)) && (
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
              {experience && <span className="text-sm font-bold text-text-secondary">{experience}</span>}
              {card && !card.verified && <CardStatusBadge verified={false} />}
            </div>
          )}
        </div>
      }
      actions={
        <>
          <ArrowLink to={paths.card(target.cardId)} className="mr-auto">
            카드 상세로 가기
          </ArrowLink>
          <DialogCancel>닫기</DialogCancel>
        </>
      }
    >
      <div className="flex flex-col gap-2 rounded-control bg-accent-subtle px-4 py-3.5">
        <h3 className="text-label-sm font-bold text-accent-strong">이 요건과 겹친 태그</h3>
        <ul className="flex flex-wrap gap-1.5">
          {target.overlap.map((tag) => (
            <li key={tag} className="rounded-full bg-surface px-2.5 py-1 text-xs font-bold text-accent-strong">
              {tag}
            </li>
          ))}
        </ul>
      </div>
      {card ? (
        <>
          <section className="flex flex-col gap-1.5">
            <h3 className={subLabel}>요약</h3>
            <p className="text-lg font-medium">{card.summary}</p>
          </section>
          {STORY_PARTS.some(([key]) => card.star[key]) && (
            <section className="flex flex-col gap-4 border-t border-border-subtle pt-5">
              <h3 className="font-bold">이야기</h3>
              {STORY_PARTS.filter(([key]) => card.star[key]).map(([key, label]) => (
                <div key={key} className="flex flex-col gap-1">
                  <h4 className={subLabel}>{label}</h4>
                  <p>
                    <FlaggedText text={card.star[key]} />
                  </p>
                </div>
              ))}
            </section>
          )}
          <section className={divided}>
            <h3 className="font-bold">근거 숫자</h3>
            <FactList facts={card.facts} />
          </section>
          <section className={divided}>
            <h3 className="font-bold">태그</h3>
            <ul className="flex flex-wrap gap-1.5">
              {card.tags.map((tag) => (
                <Chip key={tag}>{tag}</Chip>
              ))}
            </ul>
          </section>
        </>
      ) : (
        <p role="status" className="text-sm text-text-secondary">
          {current && "error" in current ? current.error : "카드를 읽는 중입니다."}
        </p>
      )}
    </AppDialog>
  )
}
