import { Dialog } from "@base-ui/react/dialog"
import { useEffect, useState } from "react"

import { Button } from "@/components/ui/button"
import { getCard } from "@/domain/card/api"
import { CardStatusBadge, FactList, StoryPart } from "@/domain/card/card-bits"
import { STORY_PARTS } from "@/domain/card/card-rules"
import type { CardDetail } from "@/domain/card/types"
import { DialogCancel } from "@/shared/components/app-dialog"
import { ArrowLink } from "@/shared/components/arrow-link"
import { Chip } from "@/shared/components/chip"
import { subLabel } from "@/shared/components/heading"
import { ChevronLeftIcon, ChevronRightIcon, XIcon } from "@/shared/icons"
import { errorMessage } from "@/shared/lib/api"
import { paths } from "@/shared/lib/paths"

import { cardPosition, isOverlapTag, stepCard } from "../requirement-cards"
import type { CardMatch, RequirementCard } from "../types"

/** 창에 띄운 것: 어느 목록(요건 하나, 또는 맞는 카드 전체)의 몇 번째 카드인지 */
export interface CardPreview {
  /** 머리의 굵은 글: 요건 글, 또는 "맞는 카드" */
  label: string
  /** 겹친 태그 상자의 제목 */
  overlapTitle: string
  cards: RequirementCard[]
  index: number
}

interface CardPreviewDialogProps {
  preview: CardPreview | null
  /** 맞는 카드(제목과 경험 이름). 카드를 읽기 전에도 제목을 보인다 */
  matches: Map<string, CardMatch>
  onIndex: (index: number) => void
  onClose: () => void
}

/**
 * 앞뒤 버튼은 끝 카드에서 꺼져도 초점을 잃지 않게 focusableWhenDisabled로 둔다. 그러면 disabled 속성 대신 aria-disabled가 붙어
 * 공용 Button의 disabled:opacity-50이 듣지 않으므로 같은 흐림을 여기서 준다.
 */
const stepButton = "aria-disabled:pointer-events-none aria-disabled:opacity-35"

/** 묶음 사이의 가는 줄 */
const divided = "flex flex-col gap-2 border-t border-border-subtle pt-5"

/** 머리·본문·바닥의 옆 여백. 휴대폰은 16px */
const sidePad = "px-6 max-sm:px-4"

/**
 * 카드 미리 보기 창(최종 시안 "요건과 내 카드"의 카드 창). 작은 창(AppDialog)과 종류가 달라 틀을 따로 둔다:
 * 640px, 머리(요건·자리·앞뒤·닫기, 경험 이름, 제목, 기간)와 바닥은 고정이고 본문만 스크롤된다. 휴대폰은 아래에서 올라온다.
 * Esc·덮개 클릭으로 닫기와 닫으면 연 버튼으로 초점 돌려주기는 base-ui Dialog가 한다.
 * 카드 내용은 창을 열거나 넘길 때 GET /api/cards/{cardId}로 읽는다(공고 문서 4.8).
 */
export function CardPreviewDialog({ preview, matches, onIndex, onClose }: CardPreviewDialogProps) {
  const target = preview?.cards[preview.index]
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

  return (
    <Dialog.Root open={!!target} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-black/45" />
        <div className="pointer-events-none fixed inset-0 z-50 flex items-center justify-center px-4 py-6 max-sm:items-end max-sm:p-0">
          {/* 창은 뿌리 틀 밖(body)에 그려지므로 뿌리 틀의 글자 규칙(root-layout.tsx)을 다시 건다 */}
          <Dialog.Popup className="pointer-events-auto flex max-h-full w-full max-w-[640px] flex-col overflow-hidden rounded-panel border border-border-subtle bg-surface-raised text-base leading-body tracking-body break-keep text-pretty text-foreground shadow-floating outline-none max-sm:max-h-[92vh] max-sm:max-w-none max-sm:rounded-b-none max-sm:border-b-0">
            {preview && target && <PreviewBody preview={preview} target={target} matches={matches} loaded={loaded} onIndex={onIndex} />}
          </Dialog.Popup>
        </div>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

interface PreviewBodyProps {
  preview: CardPreview
  target: RequirementCard
  matches: Map<string, CardMatch>
  loaded: { id: string; card: CardDetail } | { id: string; error: string } | null
  onIndex: (index: number) => void
}

function PreviewBody({ preview, target, matches, loaded, onIndex }: PreviewBodyProps) {
  const { label, overlapTitle, cards, index } = preview
  const prev = stepCard(index, -1, cards.length)
  const next = stepCard(index, 1, cards.length)
  const current = loaded?.id === target.cardId ? loaded : null
  const card = current && "card" in current ? current.card : null
  const match = matches.get(target.cardId)
  const experience = card?.experience ?? match?.experience ?? ""
  const where = card ? [card.period, card.org, card.role].filter(Boolean).join(" · ") : ""

  return (
    <>
      <div className="flex flex-none flex-col gap-3 border-b border-border-subtle pt-4 pr-4 pb-5 pl-6 max-sm:pl-4">
        <div className="flex items-center justify-between gap-2">
          <p role="status" className="min-w-0 text-label-sm text-text-secondary">
            <strong className="font-bold text-foreground">{label}</strong> · {cardPosition(index, cards.length)}
          </p>
          <div className="flex flex-none items-center gap-0.5">
            <Button type="button" variant="ghost" size="icon" aria-label="이전 카드" focusableWhenDisabled disabled={prev === null} className={stepButton} onClick={() => prev !== null && onIndex(prev)}>
              <ChevronLeftIcon aria-hidden="true" />
            </Button>
            <Button type="button" variant="ghost" size="icon" aria-label="다음 카드" focusableWhenDisabled disabled={next === null} className={stepButton} onClick={() => next !== null && onIndex(next)}>
              <ChevronRightIcon aria-hidden="true" />
            </Button>
            <Dialog.Close render={<Button type="button" variant="ghost" size="icon" aria-label="닫기" />}>
              <XIcon aria-hidden="true" />
            </Dialog.Close>
          </div>
        </div>
        <div className="flex flex-col gap-1.5 pr-2">
          {(experience || (card && !card.verified)) && (
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
              {experience && <span className="text-sm font-bold text-text-secondary">{experience}</span>}
              {card && !card.verified && <CardStatusBadge verified={false} />}
            </div>
          )}
          <Dialog.Title className="font-heading text-2xl leading-[1.2] font-bold tracking-[-0.02em]">{card?.title ?? match?.title ?? ""}</Dialog.Title>
          {where && <p className="text-sm text-text-secondary">{where}</p>}
        </div>
      </div>

      <div className={`flex min-h-0 flex-auto flex-col gap-5 overflow-y-auto py-5 ${sidePad}`}>
        <div className="flex flex-col gap-2 rounded-control bg-accent-subtle px-4 py-3.5">
          <h3 className="text-label-sm font-bold text-accent-strong">{overlapTitle}</h3>
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
                  <StoryPart key={key} label={label} text={card.star[key]} heading="h4" className="flex flex-col gap-1" />
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
                {card.tags.map((tag) =>
                  isOverlapTag(tag, target.overlap) ? (
                    // Chip은 클래스를 덮어쓸 수 없어(joinClasses) 겹친 태그는 같은 크기로 따로 그린다
                    <li key={tag} className="rounded-full bg-accent-subtle px-2.5 py-1 text-xs font-bold text-accent-strong">
                      {tag}
                    </li>
                  ) : (
                    <Chip key={tag}>{tag}</Chip>
                  )
                )}
              </ul>
            </section>
          </>
        ) : (
          <p role="status" className="text-sm text-text-secondary">
            {current && "error" in current ? current.error : "카드를 읽는 중입니다."}
          </p>
        )}
      </div>

      <div className={`flex flex-none flex-wrap items-center justify-between gap-2 border-t border-border-subtle py-3 ${sidePad}`}>
        <ArrowLink to={paths.card(target.cardId)}>카드 상세로 가기</ArrowLink>
        <DialogCancel>닫기</DialogCancel>
      </div>
    </>
  )
}
