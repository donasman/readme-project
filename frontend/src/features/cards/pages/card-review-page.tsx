import { useState } from "react"
import { Link, useLoaderData, type LoaderFunctionArgs } from "react-router"

import { Button, buttonVariants } from "@/components/ui/button"
import { FactList, StoryPart } from "@/domain/card/card-bits"
import { isOptionalPart, STORY_PARTS } from "@/domain/card/card-rules"
import type { CardDetail } from "@/domain/card/types"
import { AppDialog, DialogCancel } from "@/shared/components/app-dialog"
import { Chip } from "@/shared/components/chip"
import { sectionTitle, subLabel, subpageTitle } from "@/shared/components/heading"
import { columns, mainColumn, sideColumn } from "@/shared/components/page-layout"
import { Panel } from "@/shared/components/panel"
import { StatusIcon } from "@/shared/components/status-icon"
import { CheckIcon, Trash2Icon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { docTitle, joinClasses } from "@/shared/lib/utils"

import { deleteCard, getUnverifiedCards, updateCard, verifyCard } from "../api"
import { groupByExperience, orgAndRole, sceneNumber, startFrom } from "../card-rules"
import { dropFlaggedStory, markUsedNumbers, storyFlaggedValues } from "../card-text"
import { CardsSubpage, ProgressBar, StatBox } from "../components/card-bits"

/** 확인을 기다리는 카드를 읽는다. 같은 경험의 장면은 이어서 본다. ?card=id 가 있으면 그 카드부터 본다. */
export async function cardReviewLoader({ request }: LoaderFunctionArgs) {
  const cards = groupByExperience(await getUnverifiedCards()).flatMap((block) => block.cards)
  const start = new URL(request.url).searchParams.get("card")
  return { cards: startFrom(cards, start) }
}

/** 카드 확인(6차): 뽑은 카드를 원문과 나란히 한 장씩 보고 확인·나중에·버리기를 고른다. */
export function CardReviewPage() {
  const { cards } = useLoaderData<typeof cardReviewLoader>()
  const [index, setIndex] = useState(0)
  const [edited, setEdited] = useState<Record<string, CardDetail>>({})
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
        <title>{docTitle("카드 확인", "경험카드")}</title>
        <div className="flex max-w-[560px] flex-col gap-6">
          <StatusIcon>
            <CheckIcon />
          </StatusIcon>
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

  const flagged = storyFlaggedValues(card.star)
  const where = orgAndRole(card)
  const scene = sceneNumber(cards, index)
  const usedText = [...Object.values(card.star), ...card.facts].join(" ")
  // 어려움과 배운 점은 선택이라 비었으면 보이지 않는다
  const story = STORY_PARTS.filter(([key]) => card.star[key] || !isOptionalPart(key))

  // 확인과 겹치면 뒤에 오는 고치기가 확인이 옮긴 근거 숫자를 예전 목록으로 덮어서, 끝날 때까지 다른 버튼과 함께 막는다
  async function dropSentence() {
    if (!card) return
    const { experience, title, period, org, role, summary, tags, usableFor, star, facts } = card
    setBusy(true)
    try {
      const updated = await updateCard(card.id, { experience, title, period, org, role, summary, tags, usableFor, facts, star: dropFlaggedStory(star) })
      setEdited((e) => ({ ...e, [card.id]: updated }))
    } finally {
      setBusy(false)
    }
  }

  return (
    <CardsSubpage>
      <title>{docTitle("카드 확인", "경험카드")}</title>
      <div className="flex flex-col gap-6">
        <div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-3">
          <div className="flex min-w-0 flex-col gap-2">
            <h1 className={subpageTitle}>카드 확인</h1>
            <p className="text-text-secondary">
              {card.sourceName ? `${card.sourceName}에서 뽑은 카드입니다.` : "확인을 기다리는 카드입니다."} 내 경험과 맞는지 한 장씩 봅니다.
            </p>
          </div>
          <div className="flex min-w-40 flex-[0_1_220px] flex-col gap-2">
            <span aria-live="polite" className="text-sm font-bold">
              {index + 1} / {total}장
            </span>
            <ProgressBar label="확인한 카드" value={index} max={total} />
          </div>
        </div>

        <div className={columns}>
          <Panel as="article" aria-labelledby="review-title" className={joinClasses(mainColumn, "gap-5 p-6")}>
            <div className="flex flex-col gap-2">
              {(card.experience || card.period) && (
                <span className="text-label-sm text-text-secondary">
                  {card.experience && <strong className="font-bold">{card.experience}</strong>}
                  {card.experience && card.period && " · "}
                  {card.period}
                </span>
              )}
              <h2 id="review-title" className="text-[22px] leading-[1.3] font-bold tracking-[-0.02em]">
                {card.title}
              </h2>
              {where && <p className="text-sm text-text-secondary">{where}</p>}
            </div>
            {scene && (
              <p className="text-sm text-text-secondary">
                같은 경험에서 뽑은 장면 {scene.of}장 가운데 {scene.nth}번째입니다.
              </p>
            )}
            <p className="font-medium">{card.summary}</p>
            <div className="flex flex-wrap items-center gap-1.5">
              {card.tags.map((tag) => (
                <Chip as="span" key={tag}>
                  {tag}
                </Chip>
              ))}
              <span className="ml-1.5 text-xs text-text-secondary">{card.usableFor.join(" · ")}</span>
            </div>

            {story.map(([key, label], i) => (
              <StoryPart
                key={key}
                label={label}
                text={card.star[key]}
                className={i === 0 ? "flex flex-col gap-1.5 border-t border-border-subtle pt-5" : "flex flex-col gap-1.5"}
                empty={
                  <p>
                    <span className="text-sm text-text-secondary">아직 적지 않았습니다.</span>
                  </p>
                }
              />
            ))}
            {flagged.length > 0 && (
              <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3 rounded-control bg-highlight-subtle p-4">
                <p className="min-w-0 flex-[1_1_260px] text-sm">
                  <strong className="font-bold">{flagged.join(", ")}</strong>는 자료 원문에 없는 숫자입니다. 맞는 숫자면 고쳐 적고, 아니면 지워 주세요.
                </p>
                <div className="flex flex-wrap gap-2">
                  <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => void dropSentence()}>
                    이 문장 지우기
                  </Button>
                  <Link to={paths.cardEdit(card.id)} className={buttonVariants({ variant: "outline", size: "sm" })}>
                    직접 고치기
                  </Link>
                </div>
              </div>
            )}

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
              // 조각은 5,000자까지라 높이를 정해 안에서 민다. 키보드로도 밀 수 있게 초점을 받는다
              <div role="region" tabIndex={0} aria-labelledby="review-source" className="flex max-h-[560px] flex-col gap-3 overflow-y-auto rounded-panel bg-surface-muted p-5 text-sm">
                <p className="text-xs font-bold text-text-secondary">{card.sourceName ?? "붙여넣은 글"} · 이 카드를 뽑은 부분</p>
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
              <p className="text-sm text-text-secondary">이 카드가 나온 원문이 없습니다. 직접 만든 카드이거나 자료를 지운 카드입니다.</p>
            )}
            <p className="text-label-sm text-text-secondary">굵은 숫자는 카드에 쓰인 숫자입니다. 확인하면 이야기에만 있는 숫자가 근거 숫자로 옮겨집니다.</p>
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
