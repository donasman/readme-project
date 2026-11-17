import { useMemo } from "react"
import { Link, useLoaderData } from "react-router"

import SegmentedControl from "@/components/arc/segmented-control/segmented-control"
import { buttonVariants } from "@/components/ui/button"
import { ArrowLink } from "@/shared/components/arrow-link"
import { Heading, subLabel } from "@/shared/components/heading"
import { Page } from "@/shared/components/page"
import { pageHead } from "@/shared/components/page-layout"
import { ToggleButton } from "@/shared/components/toggle-button"
import { CircleAlertIcon, FileUpIcon, PlusIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { docTitle } from "@/shared/lib/utils"

import { getCards } from "../api"
import { CARD_STATUS_LABELS, countUnverified, filterCards, knownTag, orderTags, type CardStatus } from "../card-rules"
import { CardItem } from "../components/card-item"
import { useCardFilters } from "../use-card-filters"

export function cardsLoader() {
  return getCards()
}

const STATUS_OPTIONS = (Object.keys(CARD_STATUS_LABELS) as CardStatus[]).map((value) => ({ value, label: CARD_STATUS_LABELS[value] }))

/** 경험카드 목록(시안 3종의 "A · 카드 목록"). */
export function CardsPage() {
  const { cards, tags } = useLoaderData<typeof cardsLoader>()
  const [urlFilters, setFilters] = useCardFilters()
  const tag = knownTag(urlFilters.tag, tags)
  const filters = useMemo(() => ({ ...urlFilters, tag }), [urlFilters, tag])
  const shown = useMemo(() => filterCards(cards, filters), [cards, filters])
  const orderedTags = useMemo(() => orderTags(tags), [tags])
  const unverified = countUnverified(cards)

  return (
    <Page narrow className="gap-8 pt-10">
      <title>{docTitle("경험카드")}</title>
      <div className={pageHead}>
        <div className="flex min-w-0 flex-col gap-2">
          <Heading level={1} size="text-3xl">
            경험카드
          </Heading>
          <p className="text-text-secondary">문항 유형별로 꺼내 쓸 경험을 카드 한 장씩 정리합니다.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {/* 직접 만들기는 시안이 없어 카드 고치기 화면을 빈 카드로 연다 */}
          <Link to={paths.cardNew()} className={buttonVariants({ variant: "outline" })}>
            <PlusIcon data-icon="inline-start" aria-hidden="true" />
            직접 만들기
          </Link>
          <Link to={paths.cardSources()} className={buttonVariants()}>
            <FileUpIcon data-icon="inline-start" aria-hidden="true" />
            자료로 카드 만들기
          </Link>
        </div>
      </div>

      {unverified > 0 && (
        <section
          aria-label="확인이 필요한 카드"
          className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-control bg-highlight-subtle px-5 py-4"
        >
          <span className="inline-flex h-7 flex-none items-center gap-1.5 rounded-full bg-highlight pr-3 pl-2 text-label-sm font-bold whitespace-nowrap text-highlight-foreground">
            <CircleAlertIcon aria-hidden="true" className="size-4" />
            <span>확인 필요 {unverified}장</span>
          </span>
          <p className="min-w-0 flex-[1_1_320px] text-sm">
            확인을 기다리는 카드가 {unverified}장 있습니다. 원문에 없는 숫자는 [확인 필요]로 표시해 두었습니다.
          </p>
          <ArrowLink to={paths.cardReview()} className="min-h-control-sm flex-none">
            카드 확인하기
          </ArrowLink>
        </section>
      )}

      <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3">
        <SegmentedControl
          label="목록 보기"
          value={filters.status}
          onValueChange={(status) => setFilters({ status: status as CardStatus })}
          options={STATUS_OPTIONS}
        />
        {orderedTags.length > 0 && (
          <div role="group" aria-label="태그로 거르기" className="flex flex-wrap items-center gap-2">
            <span className={subLabel}>태그</span>
            {orderedTags.map((tag) => {
              const on = filters.tag === tag.name
              return (
                <ToggleButton key={tag.name} pressed={on} onClick={() => setFilters({ tag: on ? "" : tag.name })}>
                  {tag.name}
                </ToggleButton>
              )
            })}
          </div>
        )}
      </div>

      {shown.length > 0 ? (
        <div className="grid grid-cols-[repeat(auto-fit,minmax(min(320px,100%),1fr))] gap-5">
          {shown.map((card) => (
            <CardItem key={card.id} card={card} />
          ))}
        </div>
      ) : (
        <p className="py-10 text-center text-sm text-text-secondary">
          {cards.length === 0 ? "아직 카드가 없습니다." : "조건에 맞는 카드가 없습니다."}
        </p>
      )}
    </Page>
  )
}
