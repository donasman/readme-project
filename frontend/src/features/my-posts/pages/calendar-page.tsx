import { Link, useLoaderData, useSearchParams } from "react-router"

import { Button } from "@/components/ui/button"
import { STAGE_LABELS } from "@/domain/posting/my-state"
import { PostingLink } from "@/domain/posting/posting-link"
import { Heading, sectionTitle } from "@/shared/components/heading"
import { MySpaceNav } from "@/shared/components/my-space-nav"
import { Page } from "@/shared/components/page"
import { pageHead } from "@/shared/components/page-layout"
import { Panel, PanelRow } from "@/shared/components/panel"
import { Pill } from "@/shared/components/pill"
import { CheckIcon, ChevronLeftIcon, ChevronRightIcon } from "@/shared/icons"
import { formatMonthDayWeekday } from "@/shared/lib/date"
import { paths } from "@/shared/lib/paths"
import { docTitle, joinClasses } from "@/shared/lib/utils"

import { getMyPostings, getSchedules } from "../api"
import { addMonths, formatMonth, isMonth, monthCells, monthEvents, monthOf, monthSchedules } from "../calendar"
import { DdayBadge } from "../components/my-bits"

export async function calendarLoader() {
  const [{ today, postings }, schedules] = await Promise.all([getMyPostings(), getSchedules()])
  return { today, postings, schedules }
}

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"]

/**
 * 달력(시안 9차): 내 공고의 마감일과 전형 일정(메일 설계 4.5)을 한 달 칸에 놓고 아래에 그달의 마감과 일정을 모은다.
 * 768px 미만은 목록만. 달은 ?month=에 둔다.
 */
export function CalendarPage() {
  const { today, postings, schedules } = useLoaderData<typeof calendarLoader>()
  const [params, setParams] = useSearchParams()
  const asked = params.get("month")
  const month = isMonth(asked) ? asked : monthOf(today)
  const go = (next: string) => setParams(next === monthOf(today) ? {} : { month: next }, { replace: true, preventScrollReset: true })
  const cells = monthCells(month, postings, today, schedules)
  const events = monthEvents(month, postings, today)
  const steps = monthSchedules(month, schedules)
  const monthNumber = Number(month.slice(5))

  return (
    <Page narrow className="gap-6 pt-10">
      <title>{docTitle("달력")}</title>
      <MySpaceNav current="calendar" />
      <div className={pageHead}>
        <div className="flex flex-col gap-2">
          <Heading level={1} size="text-3xl">
            달력
          </Heading>
          <p className="text-text-secondary">내 공고의 마감일과 전형 일정이 날짜에 놓입니다.</p>
        </div>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" size="icon-sm" aria-label="이전 달" onClick={() => go(addMonths(month, -1))}>
            <ChevronLeftIcon aria-hidden="true" />
          </Button>
          <span aria-live="polite" className="min-w-27 text-center font-bold">
            {formatMonth(month)}
          </span>
          <Button type="button" variant="outline" size="icon-sm" aria-label="다음 달" onClick={() => go(addMonths(month, 1))}>
            <ChevronRightIcon aria-hidden="true" />
          </Button>
          <Button type="button" variant="outline" size="sm" onClick={() => go(monthOf(today))}>
            오늘
          </Button>
        </div>
      </div>

      {/* 달력 칸은 넓을 때만. 좁으면 아래 목록만 본다. 칩은 3일 안(진한 색), 그 뒤(옅은 색), 지난 마감(줄 그음), 지원함(체크)으로 나눈다 */}
      <p className="hidden flex-wrap items-center gap-x-4 gap-y-2 text-label-sm text-text-secondary md:flex">
        <span className="inline-flex items-center gap-1.5">
          <span className="rounded-[6px] bg-primary px-1.5 font-bold text-primary-foreground">D-2</span>3일 안 마감
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="rounded-[6px] bg-accent-subtle px-1.5 font-bold text-accent-strong">D-7</span>그 뒤
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="rounded-[6px] bg-surface-sunken px-1.5 font-bold text-foreground line-through">회사</span>지난 마감
        </span>
        <span className="inline-flex items-center gap-1.5">
          <CheckIcon aria-hidden="true" className="size-3.5 text-foreground" />지원함
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="rounded-[6px] bg-highlight-subtle px-1.5 font-bold text-foreground">1차 면접</span>전형 일정
        </span>
      </p>
      <Panel as="div" className="hidden overflow-hidden md:block">
        <div aria-hidden="true" className="grid grid-cols-7 py-3 text-center text-xs font-bold text-text-secondary">
          {WEEKDAYS.map((d) => (
            <span key={d}>{d}</span>
          ))}
        </div>
        <div className="grid grid-cols-7">
          {cells.map((cell, i) => (
            <div
              key={cell.date ?? `empty-${i}`}
              className={joinClasses(
                "flex min-h-28 flex-col gap-1.5 border-t border-l border-t-border-subtle p-2",
                i % 7 === 0 ? "border-l-transparent" : "border-l-border-subtle",
                cell.date ? "bg-transparent" : "bg-surface-muted"
              )}
            >
              {cell.day !== null &&
                (cell.isToday ? (
                  <span className="grid size-7 place-items-center rounded-full bg-primary text-label-sm font-bold text-primary-foreground">
                    <span className="sr-only">오늘 </span>
                    {cell.day}
                  </span>
                ) : (
                  <span className={joinClasses("grid size-7 place-items-center text-label-sm", cell.isPast ? "text-text-secondary" : "text-foreground")}>{cell.day}</span>
                ))}
              {cell.events.map((e) => (
                <Link
                  key={e.postingId}
                  to={paths.myPost(e.postingId)}
                  className={joinClasses(
                    "flex items-center gap-1 rounded-[8px] px-2 py-1 text-xs leading-[1.3] font-bold",
                    e.closed ? "bg-surface-sunken text-foreground line-through" : e.soon ? "bg-primary text-primary-foreground" : "bg-accent-subtle text-accent-strong"
                  )}
                >
                  {e.applied && <CheckIcon aria-hidden="true" className="size-3 flex-none" />}
                  <span className="min-w-0 truncate">{e.closed ? e.company : `${e.due} ${e.company}`}</span>
                  <span className="sr-only">{`${e.closed ? " 지난 마감" : " 마감"}${e.applied ? ", 지원함" : ""}`}</span>
                </Link>
              ))}
              {cell.schedules.map((s) => (
                <Link
                  key={`${s.postingId}-${s.stage}`}
                  to={paths.myPost(s.postingId)}
                  className="block truncate rounded-[8px] bg-highlight-subtle px-2 py-1 text-xs leading-[1.3] font-bold text-foreground"
                >
                  {STAGE_LABELS[s.stage]}
                  {s.time && ` ${s.time}`} · {s.company}
                </Link>
              ))}
            </div>
          ))}
        </div>
      </Panel>

      <section aria-labelledby="cal-list" className="flex flex-col gap-3">
        <h2 id="cal-list" className={sectionTitle}>
          {monthNumber}월의 마감
        </h2>
        {events.length > 0 ? (
          <Panel as="ul" className="flex flex-col">
            {events.map((e, i) => (
              <PanelRow key={e.postingId} first={i === 0} className="gap-x-4 py-3.5">
                <span className="w-24 flex-none font-bold whitespace-nowrap">{formatMonthDayWeekday(e.date)}</span>
                <PostingLink id={e.postingId} company={e.company} title={e.title} className="flex-[1_1_200px]" />
                {e.closed ? <Pill tone="muted">마감</Pill> : <DdayBadge text={e.due} soon={e.soon} />}
              </PanelRow>
            ))}
          </Panel>
        ) : (
          <p className="text-sm text-text-secondary">이 달에 마감하는 내 공고가 없습니다.</p>
        )}
        <p className="text-label-sm text-text-secondary">상시 채용과 마감일을 모르는 공고는 달력에 놓이지 않습니다. 내 공고에서 봅니다.</p>
      </section>

      {steps.length > 0 && (
        <section aria-labelledby="cal-steps" className="flex flex-col gap-3">
          <h2 id="cal-steps" className={sectionTitle}>
            {monthNumber}월의 전형 일정
          </h2>
          <Panel as="ul" className="flex flex-col">
            {steps.map((s, i) => (
              <PanelRow key={`${s.postingId}-${s.stage}`} first={i === 0} className="gap-x-4 py-3.5">
                <span className="w-24 flex-none font-bold whitespace-nowrap">
                  {formatMonthDayWeekday(s.date)}
                  {s.time && <span className="block text-label-sm font-normal text-text-secondary">{s.time}</span>}
                </span>
                <PostingLink id={s.postingId} company={s.company} title={s.title} className="flex-[1_1_200px]" />
                <Pill tone="highlight">{STAGE_LABELS[s.stage]}</Pill>
              </PanelRow>
            ))}
          </Panel>
          <p className="text-label-sm text-text-secondary">전형 일정은 메일 확인함에서 반영한 메일로 들어옵니다.</p>
        </section>
      )}
    </Page>
  )
}
