import { useState } from "react"
import { Link, useLoaderData, type LoaderFunctionArgs } from "react-router"

import { Button, buttonVariants } from "@/components/ui/button"
import { AppDialog, DialogCancel } from "@/shared/components/app-dialog"
import { Chip } from "@/shared/components/chip"
import { sectionTitle, subLabel, subpageTitle } from "@/shared/components/heading"
import { columns, mainColumn, sideColumn } from "@/shared/components/page-layout"
import { Panel } from "@/shared/components/panel"
import { CheckIcon, Trash2Icon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { joinClasses } from "@/shared/lib/utils"

import { deleteCard, getUnverifiedCards, updateCard, verifyCard } from "../api"
import { orgAndRole, STAR_PARTS, startFrom } from "../card-rules"
import { dropFlaggedSentences, flaggedValues, markUsedNumbers } from "../card-text"
import { CardsSubpage, FactList, FlaggedText, ProgressBar, StatBox } from "../components/card-bits"
import type { CardDetail } from "../types"

/** 확인을 기다리는 카드를 읽는다. ?card=id 가 있으면 그 카드부터 본다. */
export async function cardReviewLoader({ request }: LoaderFunctionArgs) {
  const cards = await getUnverifiedCards()
  const start = Number(new URL(request.url).searchParams.get("card"))
  return { cards: startFrom(cards, start) }
}

/** 결과는 확인 필요 상자와 함께 따로 그린다. */
const STAR_BEFORE_RESULT = STAR_PARTS.filter(([key]) => key !== "result")

/** 카드 확인(6차): 뽑은 카드를 원문과 나란히 한 장씩 보고 확인·나중에·버리기를 고른다. */
export function CardReviewPage() {
  const { cards } = useLoaderData<typeof cardReviewLoader>()
  const [index, setIndex] = useState(0)
  const [edited, setEdited] = useState<Record<number, CardDetail>>({})
  const [counts, setCounts] = useState({ verified: 0, later: 0, dropped: 0 })
  const [busy, setBusy] = useState(false)
  const [dropping, setDropping] = useState(false)
  const total = cards.length
  const card = index < total ? (edited[cards[index].id] ?? cards[index]) : null

  async function next(kind: keyof typeof counts, act?: () => Promise<unknown>) {
    setBusy(true)
    try {
      await act?.()
      setCounts((c) => ({ ...c, [kind]: c[kind] + 1 }))
      setIndex((i) => i + 1)
    } finally {
      setBusy(false)
    }
  }

  if (!card) {
    return (
      <CardsSubpage>
        <div className="flex max-w-[560px] flex-col gap-6">
          <span aria-hidden="true" className="inline-flex size-12 items-center justify-center rounded-control bg-accent-subtle text-accent-strong">
            <CheckIcon className="size-6" />
          </span>
          <div className="flex flex-col gap-2">
            <h1 className={subpageTitle}>{total ? `${total}장을 모두 봤습니다` : "확인할 카드가 없습니다"}</h1>
            <p className="text-text-secondary">확인한 카드는 공고와 맞춰 볼 때 쓰입니다.</p>
          </div>
          {total > 0 && (
            <>
              <dl className="grid grid-cols-3 gap-3">
                <StatBox label="확인함" value={`${counts.verified}장`} />
                <StatBox label="나중에 보기" value={`${counts.later}장`} />
                <StatBox label="버림" value={`${counts.dropped}장`} />
              </dl>
              <p className="text-sm text-text-secondary">나중에 보기로 둔 카드는 목록에 "확인 필요"로 남아 있습니다.</p>
            </>
          )}
          <Link to={paths.cards()} className={buttonVariants({ className: "self-start px-6" })}>
            카드 목록으로
          </Link>
        </div>
      </CardsSubpage>
    )
  }

  const flagged = flaggedValues(card.star.result)
  const where = orgAndRole(card)
  const usedText = [card.star.situation, card.star.task, card.star.action, card.star.result, ...card.facts].join(" ")

  async function dropSentence() {
    if (!card) return
    const { title, period, org, role, summary, tags, usableFor, star, facts } = card
    const updated = await updateCard(card.id, { title, period, org, role, summary, tags, usableFor, facts, star: { ...star, result: dropFlaggedSentences(star.result) } })
    setEdited((e) => ({ ...e, [card.id]: updated }))
  }

  return (
    <CardsSubpage>
      <div className="flex flex-col gap-6">
        <div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-3">
          <div className="flex min-w-0 flex-col gap-2">
            <h1 className={subpageTitle}>카드 확인</h1>
            <p className="text-text-secondary">
              {card.sourceName ? `${card.sourceName}에서 뽑은 카드입니다.` : "자료에서 뽑은 카드입니다."} 내 경험과 맞는지 한 장씩 봅니다.
            </p>
          </div>
          <div className="flex min-w-40 flex-[0_1_220px] flex-col gap-2">
            <span className="text-sm font-bold">
              {index + 1} / {total}장
            </span>
            <ProgressBar label="확인한 카드" value={index} max={total} />
          </div>
        </div>

        <div className={columns}>
          <Panel as="article" aria-labelledby="review-title" className={joinClasses(mainColumn, "gap-5 p-6")}>
            <div className="flex flex-col gap-2">
              {card.period && <span className="text-label-sm text-text-secondary">{card.period}</span>}
              <h2 id="review-title" className="text-[22px] leading-[1.3] font-bold tracking-[-0.02em]">
                {card.title}
              </h2>
              {where && <p className="text-sm text-text-secondary">{where}</p>}
            </div>
            <p className="font-medium">{card.summary}</p>
            <div className="flex flex-wrap items-center gap-1.5">
              {card.tags.map((tag) => (
                <Chip as="span" key={tag}>
                  {tag}
                </Chip>
              ))}
              <span className="ml-1.5 text-xs text-text-secondary">{card.usableFor.join(" · ")}</span>
            </div>

            {STAR_BEFORE_RESULT.map(([key, label], i) => (
              <div key={key} className={i === 0 ? "flex flex-col gap-1.5 border-t border-border-subtle pt-5" : "flex flex-col gap-1.5"}>
                <h3 className={subLabel}>{label}</h3>
                <p>{card.star[key] || <span className="text-sm text-text-secondary">아직 적지 않았습니다.</span>}</p>
              </div>
            ))}
            <div className="flex flex-col gap-2.5">
              <h3 className={subLabel}>결과</h3>
              <p>{card.star.result ? <FlaggedText text={card.star.result} /> : <span className="text-sm text-text-secondary">아직 적지 않았습니다.</span>}</p>
              {flagged.length > 0 && (
                <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3 rounded-control bg-highlight-subtle p-4">
                  <p className="min-w-0 flex-[1_1_260px] text-sm">
                    <strong className="font-bold">{flagged.join(", ")}</strong>는 자료 원문에 없는 숫자입니다. 맞는 숫자면 고쳐 적고, 아니면 지워 주세요.
                  </p>
                  <div className="flex flex-wrap gap-2">
                    <Button type="button" variant="outline" size="sm" onClick={() => void dropSentence()}>
                      이 문장 지우기
                    </Button>
                    <Link to={paths.cardEdit(card.id)} className={buttonVariants({ variant: "outline", size: "sm" })}>
                      직접 고치기
                    </Link>
                  </div>
                </div>
              )}
            </div>

            <div className="flex flex-col gap-2 border-t border-border-subtle pt-5">
              <h3 className={subLabel}>근거 숫자</h3>
              <FactList facts={card.facts} />
            </div>
          </Panel>

          <aside aria-labelledby="review-source" className={joinClasses(sideColumn(320), "gap-3")}>
            <h2 id="review-source" className={sectionTitle}>
              자료 원문
            </h2>
            {card.sourceExcerpt ? (
              <div className="flex flex-col gap-3 rounded-panel bg-surface-muted p-5 text-sm">
                <p className="text-xs font-bold text-text-secondary">{card.sourceName} · 이 카드를 뽑은 부분</p>
                {markUsedNumbers(card.sourceExcerpt, usedText).map((parts, i) => (
                  <p key={i}>
                    {parts.map((part, j) =>
                      part.flagged ? (
                        <strong key={j} className="font-bold text-accent-strong">
                          {part.text}
                        </strong>
                      ) : (
                        <span key={j}>{part.text}</span>
                      )
                    )}
                  </p>
                ))}
              </div>
            ) : (
              <p className="text-sm text-text-secondary">자료가 지워져 원문을 볼 수 없습니다.</p>
            )}
            <p className="text-label-sm text-text-secondary">굵은 숫자는 카드에 쓰인 숫자입니다. 확인하면 결과에만 있는 숫자가 근거 숫자로 옮겨집니다.</p>
          </aside>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3 border-t border-border pt-6">
          <Button type="button" variant="ghost" className="px-4" disabled={busy} onClick={() => setDropping(true)}>
            <Trash2Icon aria-hidden="true" className="text-danger" />
            이 카드 버리기
          </Button>
          <div className="flex flex-wrap items-center gap-2">
            <Button type="button" variant="outline" disabled={busy} onClick={() => void next("later")}>
              나중에 보기
            </Button>
            <Button
              type="button"
              className="pr-6 has-data-[icon=inline-start]:pl-5"
              disabled={busy}
              onClick={() => void next("verified", () => verifyCard(card.id))}
            >
              <CheckIcon data-icon="inline-start" aria-hidden="true" />
              확인하고 다음
            </Button>
          </div>
        </div>
      </div>

      {/* 카드 상세의 "카드 지우기" 창과 같은 창이다. 지우면 다음 카드로 넘어간다 */}
      <AppDialog
        open={dropping}
        onOpenChange={setDropping}
        title="카드를 지울까요?"
        actions={
          <>
            <DialogCancel />
            <Button
              type="button"
              variant="danger"
              disabled={busy}
              onClick={() => {
                setDropping(false)
                void next("dropped", () => deleteCard(card.id))
              }}
            >
              지우기
            </Button>
          </>
        }
      >
        <p className="text-sm">"{card.title}" 카드가 지워집니다. 되돌릴 수 없습니다. 뽑아 온 자료는 남습니다.</p>
      </AppDialog>
    </CardsSubpage>
  )
}
