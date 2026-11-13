import { Link, useLoaderData, type LoaderFunctionArgs } from "react-router"

import { Button, buttonVariants } from "@/components/ui/button"
import { Chip } from "@/shared/components/chip"
import { subpageTitle } from "@/shared/components/heading"
import { Panel } from "@/shared/components/panel"
import { CheckIcon, FileTextIcon, LockIcon, PencilIcon, Trash2Icon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"

import { getCard } from "../api"
import { orgAndRole, STAR_PARTS } from "../card-rules"
import { CardStatusBadge, CardsSubpage, FactList, FlaggedText, sectionTitle, subLabel } from "../components/card-bits"

export function cardLoader({ params }: LoaderFunctionArgs) {
  return getCard(Number(params.cardId))
}

/** 카드 상세(6차 "카드 상세"). 확인할 것(검사 결과, 추출 메모)은 옆 칸에 모은다. */
export function CardPage() {
  const card = useLoaderData<typeof cardLoader>()
  const notes = card.issues.length + card.extractionNotes.length
  const where = orgAndRole(card)

  return (
    <CardsSubpage>
      <div className="flex flex-col gap-6">
        <div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-4">
          <div className="flex min-w-0 flex-[1_1_420px] flex-col gap-2.5">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
              <CardStatusBadge verified={card.verified} />
              {card.period && <span className="text-label-sm whitespace-nowrap text-text-secondary">{card.period}</span>}
            </div>
            <h1 className={subpageTitle}>{card.title}</h1>
            {where && <p className="text-text-secondary">{where}</p>}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {/* 삭제: 확인 창(7차 "창 · 카드 지우기")과 함께 붙인다 */}
            <Button variant="ghost" className="px-4">
              <Trash2Icon aria-hidden="true" className="text-danger" />
              삭제
            </Button>
            <Link to={paths.cardEdit(card.id)} className={buttonVariants({ variant: "outline" })}>
              <PencilIcon data-icon="inline-start" aria-hidden="true" />
              수정
            </Link>
            {!card.verified && (
              <Link to={paths.cardReview(card.id)} className={buttonVariants()}>
                <CheckIcon data-icon="inline-start" aria-hidden="true" />
                확인하기
              </Link>
            )}
          </div>
        </div>

        <div className="flex flex-wrap items-start gap-6">
          <div className="flex min-w-0 flex-[999_1_560px] flex-col gap-6">
            <Panel aria-labelledby="card-summary" className="flex flex-col gap-3 p-6">
              <h2 id="card-summary" className={subLabel}>
                요약
              </h2>
              <p className="text-lg font-medium">{card.summary}</p>
            </Panel>

            <Panel aria-labelledby="card-star" className="flex flex-col gap-5 p-6">
              <h2 id="card-star" className={sectionTitle}>
                STAR
              </h2>
              {STAR_PARTS.map(([key, label], i) => (
                <div key={key} className={i === 0 ? "flex flex-col gap-1.5" : "flex flex-col gap-1.5 border-t border-border-subtle pt-5"}>
                  <h3 className={subLabel}>{label}</h3>
                  {card.star[key] ? (
                    <p>
                      <FlaggedText text={card.star[key]} />
                    </p>
                  ) : (
                    <p className="text-sm text-text-secondary">아직 적지 않았습니다.</p>
                  )}
                </div>
              ))}
            </Panel>

            <Panel aria-labelledby="card-facts" className="flex flex-col gap-3 p-6">
              <h2 id="card-facts" className={sectionTitle}>
                근거 숫자
              </h2>
              <FactList facts={card.facts} />
            </Panel>
          </div>

          <aside className="flex min-w-0 flex-[1_1_300px] flex-col gap-6">
            {notes > 0 && (
              <section aria-labelledby="card-issues" className="flex flex-col gap-3 rounded-panel bg-highlight-subtle p-5">
                <h2 id="card-issues" className="text-sm font-bold">
                  확인할 것 {notes}개
                </h2>
                <ul className="flex flex-col gap-3">
                  {card.issues.map((issue) => (
                    <li key={issue.field + issue.message} className="flex flex-col gap-0.5 text-sm">
                      <span className="text-xs font-bold text-text-secondary">
                        {issue.level} · {issue.field}
                      </span>
                      <span>{issue.message}</span>
                    </li>
                  ))}
                  {card.extractionNotes.map((note) => (
                    <li key={note} className="flex flex-col gap-0.5 text-sm">
                      <span className="text-xs font-bold text-text-secondary">추출 메모</span>
                      <span>{note}</span>
                    </li>
                  ))}
                </ul>
              </section>
            )}

            <Panel aria-labelledby="card-info" className="flex flex-col gap-5 p-6">
              <h2 id="card-info" className="sr-only">
                카드 정보
              </h2>
              <div className="flex flex-col gap-2">
                <h3 className={subLabel}>태그</h3>
                <ul className="flex flex-wrap gap-1.5">
                  {card.tags.map((tag) => (
                    <Chip key={tag}>{tag}</Chip>
                  ))}
                </ul>
              </div>
              <div className="flex flex-col gap-2">
                <h3 className={subLabel}>쓸 문항 유형</h3>
                <p className="text-sm">{card.usableFor.join(" · ")}</p>
              </div>
              <div className="flex flex-col gap-2">
                <h3 className={subLabel}>공개 범위</h3>
                <p className="flex items-center gap-1.5 text-sm">
                  <LockIcon aria-hidden="true" className="size-4" />
                  <span>나만 보기</span>
                </p>
              </div>
              {card.sourceName && (
                <div className="flex flex-col gap-2">
                  <h3 className={subLabel}>뽑아 온 자료</h3>
                  <Link to={paths.cardSources()} className="-my-3 inline-flex min-h-control-md items-center gap-1.5 self-start text-sm font-bold text-primary">
                    <FileTextIcon aria-hidden="true" className="size-4" />
                    <span>{card.sourceName}</span>
                  </Link>
                </div>
              )}
            </Panel>
          </aside>
        </div>
      </div>
    </CardsSubpage>
  )
}
