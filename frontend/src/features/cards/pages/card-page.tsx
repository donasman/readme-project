import { useState } from "react"
import { Link, useLoaderData, useNavigate, type LoaderFunctionArgs } from "react-router"

import { Button, buttonVariants } from "@/components/ui/button"
import { getCard } from "@/domain/card/api"
import { CardStatusBadge, FactList, StoryPart } from "@/domain/card/card-bits"
import { isOptionalPart, STORY_PARTS } from "@/domain/card/card-rules"
import { AppDialog, DialogCancel } from "@/shared/components/app-dialog"
import { ArrowLink } from "@/shared/components/arrow-link"
import { BusyButton } from "@/shared/components/busy-button"
import { Chip } from "@/shared/components/chip"
import { sectionTitle, subLabel, subpageTitle } from "@/shared/components/heading"
import { columns, mainColumn, pageHead, sideColumn } from "@/shared/components/page-layout"
import { Panel } from "@/shared/components/panel"
import { CheckIcon, FileTextIcon, LockIcon, PencilIcon, Trash2Icon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { docTitle, joinClasses } from "@/shared/lib/utils"

import { deleteCard } from "../api"
import { orgAndRole } from "../card-rules"
import { CardsSubpage } from "../components/card-bits"

export function cardLoader({ params }: LoaderFunctionArgs) {
  return getCard(params.cardId ?? "")
}

/** 카드 상세(6차 "카드 상세"). 확인할 것(검사 결과, 추출 메모)은 옆 칸에 모은다. */
export function CardPage() {
  const card = useLoaderData<typeof cardLoader>()
  // 어려움과 배운 점은 선택이라 비었으면 보이지 않는다
  const story = STORY_PARTS.filter(([key]) => card.star[key] || !isOptionalPart(key))
  const notes = card.issues.length + card.extractionNotes.length
  const where = orgAndRole(card)
  const navigate = useNavigate()
  const [deleting, setDeleting] = useState(false)

  return (
    <CardsSubpage>
      <title>{docTitle(card.title, "경험카드")}</title>
      <div className="flex flex-col gap-6">
        <div className={pageHead}>
          <div className="flex min-w-0 flex-[1_1_420px] flex-col gap-2.5">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
              <CardStatusBadge verified={card.verified} />
              {card.period && <span className="text-label-sm whitespace-nowrap text-text-secondary">{card.period}</span>}
            </div>
            {card.experience && <p className="text-sm font-bold text-text-secondary">{card.experience}</p>}
            <h1 className={subpageTitle}>{card.title}</h1>
            {where && <p className="text-text-secondary">{where}</p>}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button variant="ghost" className="px-4" onClick={() => setDeleting(true)}>
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

        <div className={columns}>
          <div className={joinClasses(mainColumn, "gap-6")}>
            <Panel aria-labelledby="card-summary" className="flex flex-col gap-3 p-6">
              <h2 id="card-summary" className={subLabel}>
                요약
              </h2>
              <p className="text-lg font-medium">{card.summary}</p>
            </Panel>

            <Panel aria-labelledby="card-star" className="flex flex-col gap-5 p-6">
              <h2 id="card-star" className={sectionTitle}>
                이야기
              </h2>
              {story.map(([key, label], i) => (
                <StoryPart key={key} label={label} text={card.star[key]} className={i === 0 ? "flex flex-col gap-1.5" : "flex flex-col gap-1.5 border-t border-border-subtle pt-5"} />
              ))}
            </Panel>

            <Panel aria-labelledby="card-facts" className="flex flex-col gap-3 p-6">
              <h2 id="card-facts" className={sectionTitle}>
                근거 숫자
              </h2>
              <FactList facts={card.facts} />
            </Panel>
          </div>

          <aside className={joinClasses(sideColumn(300), "gap-6")}>
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
              {card.experience && (
                <div className="flex flex-col gap-2">
                  <h3 className={subLabel}>경험</h3>
                  <p className="text-sm">{card.experience}</p>
                  <ArrowLink to={paths.cardNew(card.experience)} className="min-h-control-sm self-start">
                    이 경험에 장면 더하기
                  </ArrowLink>
                </div>
              )}
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
      <AppDialog
        open={deleting}
        onOpenChange={setDeleting}
        title="카드를 지울까요?"
        actions={
          <>
            <DialogCancel />
            <BusyButton
              type="button"
              variant="danger"
              onClick={async () => {
                await deleteCard(card.id)
                await navigate(paths.cards())
              }}
            >
              지우기
            </BusyButton>
          </>
        }
      >
        <p className="text-sm">"{card.title}" 카드가 지워집니다. 되돌릴 수 없습니다. 뽑아 온 자료는 남습니다.</p>
      </AppDialog>
    </CardsSubpage>
  )
}
