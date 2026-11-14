import { Link, useLoaderData, useSearchParams } from "react-router"

import { Button } from "@/components/ui/button"
import { Heading } from "@/shared/components/heading"
import { MySpaceNav } from "@/shared/components/my-space-nav"
import { Page } from "@/shared/components/page"
import { pageHead } from "@/shared/components/page-layout"
import { Panel } from "@/shared/components/panel"
import { Pill } from "@/shared/components/pill"
import { ChevronLeftIcon, ChevronRightIcon } from "@/shared/icons"
import { formatDday } from "@/shared/lib/date"
import { paths } from "@/shared/lib/paths"
import { joinClasses } from "@/shared/lib/utils"

import { getMyPostings } from "../api"
import { addMonths, agendaDate, formatMonth, isMonth, monthCells, monthEvents, monthOf } from "../calendar"
import { DdayBadge } from "../components/my-bits"

export function calendarLoader() {
  return getMyPostings()
}

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"]

/** 달력(시안 9차): 내 공고의 마감일을 한 달 칸에 놓고 아래에 그달의 마감을 모은다. 768px 미만은 목록만. 달은 ?month=에 둔다. */
export function CalendarPage() {
  const { today, postings } = useLoaderData<typeof calendarLoader>()
  const [params, setParams] = useSearchParams()
  const asked = params.get("month")
  const month = isMonth(asked) ? asked : monthOf(today)
  const go = (next: string) => setParams(next === monthOf(today) ? {} : { month: next }, { replace: true, preventScrollReset: true })
  const cells = monthCells(month, postings, today)
  const events = monthEvents(month, postings, today)
  const monthNumber = Number(month.slice(5))

  return (
    <Page narrow className="gap-6 pt-10">
      <MySpaceNav current="calendar" />
      <div className={pageHead}>
        <div className="flex flex-col gap-2">
          <Heading level={1} size="text-3xl">
            달력
          </Heading>
          <p className="text-text-secondary">내 공고의 마감일이 날짜에 놓입니다.</p>
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

      {/* 달력 칸은 넓을 때만. 좁으면 아래 목록만 본다 */}
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
                    "block rounded-[8px] px-2 py-1 text-xs leading-[1.3] font-bold",
                    e.closed ? "bg-surface-sunken text-foreground" : "bg-accent-subtle text-accent-strong"
                  )}
                >
                  마감 · {e.company}
                </Link>
              ))}
            </div>
          ))}
        </div>
      </Panel>

      <section aria-labelledby="cal-list" className="flex flex-col gap-3">
        <h2 id="cal-list" className="text-lg font-bold">
          {monthNumber}월의 마감
        </h2>
        {events.length > 0 ? (
          <Panel as="ul" className="flex flex-col">
            {events.map((e, i) => (
              <li key={e.postingId} className={joinClasses("flex flex-wrap items-center gap-x-4 gap-y-2 border-t px-5 py-3.5", i > 0 ? "border-border-subtle" : "border-transparent")}>
                <span className="w-24 flex-none font-bold whitespace-nowrap">{agendaDate(e.date)}</span>
                <Link to={paths.myPost(e.postingId)} className="flex min-w-0 flex-[1_1_200px] flex-col gap-0.5 text-foreground">
                  <span className="text-label-sm text-text-secondary">{e.company}</span>
                  <span className="font-bold">{e.title}</span>
                </Link>
                {e.closed ? <Pill tone="muted">마감</Pill> : <DdayBadge text={formatDday(today, e.date)} soon={false} />}
              </li>
            ))}
          </Panel>
        ) : (
          <p className="text-sm text-text-secondary">이 달에 마감하는 내 공고가 없습니다.</p>
        )}
        <p className="text-label-sm text-text-secondary">상시 채용과 마감일을 모르는 공고는 달력에 놓이지 않습니다. 내 공고에서 봅니다.</p>
      </section>
    </Page>
  )
}
