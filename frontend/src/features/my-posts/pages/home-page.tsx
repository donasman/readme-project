import type { ReactNode } from "react"
import { Link, redirect, useLoaderData } from "react-router"

import { getMyGroups } from "@/domain/group/api"
import { GroupMark } from "@/domain/group/group-mark"
import { isClosed } from "@/domain/posting/deadline"
import { PostingLink } from "@/domain/posting/posting-link"
import type { FillDeadlineState } from "@/domain/posting/types"
import { ArrowLink } from "@/shared/components/arrow-link"
import { Heading, sectionTitle } from "@/shared/components/heading"
import { MySpaceNav } from "@/shared/components/my-space-nav"
import { Page } from "@/shared/components/page"
import { columns, mainColumn, sideColumn } from "@/shared/components/page-layout"
import { Panel, PanelRow } from "@/shared/components/panel"
import { ArrowRightIcon } from "@/shared/icons"
import { daysBetween, formatLongDate } from "@/shared/lib/date"
import { paths } from "@/shared/lib/paths"
import { docTitle, joinClasses } from "@/shared/lib/utils"

import { getHome } from "../api"
import { BigStat, DdayBadge, MyStatusPill } from "../components/my-bits"
import { boardCounts, dueText, sortPostings } from "../status"

/** 그룹이 없으면 홈 대신 내 그룹(빈 상태)으로 보낸다(시안 7차의 제안). */
export async function homeLoader() {
  const groups = await getMyGroups()
  if (groups.length === 0) throw redirect(paths.groups())
  return getHome()
}

const UPCOMING = 5
const WEEK = 7

/** 홈(시안 8차 "홈", 11차에서 숫자 셋): 오늘, 숫자 셋, 다가오는 마감, 확인 필요, 그룹 소식. */
export function HomePage() {
  const { today, postings, unverifiedCards, mailChanges, news } = useLoaderData<typeof homeLoader>()
  const open = sortPostings(postings, today).filter((p) => !isClosed(p.deadline, today))
  const thisWeek = open.filter((p) => p.deadline.kind === "date" && daysBetween(today, p.deadline.date) <= WEEK).length
  const unknown = open.filter((p) => p.deadline.kind === "unknown")
  const counts = boardCounts(postings)

  // 확인 필요: 확인을 기다리는 카드, 마감일을 못 읽은 공고, 메일에서 온 변경
  const todos: { key: string; content: ReactNode; to: string; state?: FillDeadlineState }[] = [
    ...(unverifiedCards > 0
      ? [{ key: "cards", to: paths.cardReview(), content: <><strong className="font-bold">카드 {unverifiedCards}장</strong>이 확인을 기다립니다</> }]
      : []),
    ...unknown.flatMap((p) => {
      const group = p.groups[0]
      return group
        ? [{ key: p.id, to: paths.groupPosts(group.id), state: { fillDeadline: group.postId }, content: <><strong className="font-bold">{p.company}</strong> 공고의 마감일을 못 읽었습니다</> }]
        : []
    }),
    ...(mailChanges.count > 0
      ? [{ key: "mail", to: paths.mail(), content: <>메일에서 온 변경 <strong className="font-bold">{mailChanges.count}건</strong>{mailChanges.first && `: ${mailChanges.first}`}</> }]
      : []),
  ]

  return (
    <Page narrow className="gap-6 pt-10">
      <title>{docTitle("홈")}</title>
      <MySpaceNav current="home" />
      <div className="flex flex-col gap-2">
        <p className="text-sm text-text-secondary">{formatLongDate(today)}</p>
        <Heading level={1} size="text-3xl">
          {thisWeek > 0 ? `${WEEK}일 안에 마감하는 공고가 ${thisWeek}건 있습니다` : `${WEEK}일 안에 마감하는 공고가 없습니다`}
        </Heading>
      </div>

      {/* 7일 안 마감은 제목이, 확인 필요는 옆의 상자가 이미 말해서 넣지 않는다(11차 시안). 진행 중은 지원 현황과 같은 셈이다 */}
      <dl className="grid grid-cols-3 gap-2 sm:gap-3">
        <BigStat label="지원한 공고" value={counts.applied} />
        <BigStat label="전형 진행 중" value={counts.active} />
        <BigStat label="아직 정하지 않은 공고" value={postings.filter((p) => p.decision === "undecided").length} />
      </dl>

      <div className={columns}>
        <section aria-labelledby="home-due" className={joinClasses(mainColumn, "gap-3")}>
          <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
            <h2 id="home-due" className={sectionTitle}>
              다가오는 마감
            </h2>
            <ArrowLink to={paths.myPosts()}>내 공고 모두 보기</ArrowLink>
          </div>
          {open.length > 0 ? (
            <Panel as="ul" className="flex flex-col">
              {open.slice(0, UPCOMING).map((p, i) => {
                const due = dueText(p, today)
                return (
                  <PanelRow key={p.id} first={i === 0} className="gap-x-4 py-4">
                    <div className="flex w-22 flex-none flex-col items-start gap-1">
                      <DdayBadge text={due.main} soon={due.soon} />
                      <span className="text-xs text-text-secondary">{due.sub}</span>
                    </div>
                    <PostingLink id={p.id} company={p.company} title={p.title} className="flex-[1_1_220px]" />
                    <div className="ml-auto flex flex-none flex-col items-end gap-1">
                      <MyStatusPill state={p} />
                      <span className="text-xs text-text-secondary">{p.groups.map((g) => g.name).join(", ")}</span>
                    </div>
                  </PanelRow>
                )
              })}
            </Panel>
          ) : (
            <p className="text-sm text-text-secondary">진행 중인 내 공고가 없습니다. 그룹 공고에서 관심을 켜면 여기에 들어옵니다.</p>
          )}
        </section>

        <aside className={joinClasses(sideColumn(320), "gap-6")}>
          <section aria-labelledby="home-todo" className="flex flex-col gap-3 rounded-panel bg-highlight-subtle p-5">
            <h2 id="home-todo" className={sectionTitle}>
              확인 필요 {todos.length}
            </h2>
            {todos.length > 0 ? (
              <ul className="flex flex-col gap-1">
                {todos.map((todo) => (
                  <li key={todo.key}>
                    <Link to={todo.to} state={todo.state} className="flex min-h-11 items-center justify-between gap-3 text-sm text-foreground">
                      <span>{todo.content}</span>
                      <ArrowRightIcon aria-hidden="true" className="size-4 flex-none" />
                    </Link>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm">지금 확인할 것이 없습니다.</p>
            )}
          </section>

          <Panel aria-labelledby="home-news" className="flex flex-col gap-4 p-6">
            <h2 id="home-news" className={sectionTitle}>
              그룹 소식
            </h2>
            {news.length > 0 ? (
              <ul className="flex flex-col gap-1">
                {news.map((n) => (
                  <li key={`${n.group.id}-${n.postId}-${n.text}`}>
                    <Link to={n.postId ? paths.groupPost(n.group.id, n.postId) : paths.groupPosts(n.group.id)} className="flex min-h-13 items-center gap-3 text-foreground">
                      <GroupMark group={n.group} />
                      <span className="flex min-w-0 flex-col">
                        <span className="text-sm font-bold">{n.group.name}</span>
                        <span className="text-label-sm text-text-secondary">{n.text}</span>
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-text-secondary">새 소식이 없습니다.</p>
            )}
          </Panel>
        </aside>
      </div>
    </Page>
  )
}
