import { useRef, useState } from "react"
import { Link, useLoaderData, type LoaderFunctionArgs } from "react-router"

import { buttonVariants } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { DISCLOSURE_LABELS } from "@/domain/group/disclosure"
import { Avatar } from "@/shared/components/avatar"
import { BackLink } from "@/shared/components/back-link"
import { Chip } from "@/shared/components/chip"
import { Dot } from "@/shared/components/dot"
import { Heading, sectionTitle, subLabel } from "@/shared/components/heading"
import { Page } from "@/shared/components/page"
import { columns, mainColumn, sideColumn } from "@/shared/components/page-layout"
import { Panel, PanelNote } from "@/shared/components/panel"
import { PlaceholderLink } from "@/shared/components/placeholder-link"
import { ExternalLinkIcon } from "@/shared/icons"
import { formatDateWeekday } from "@/shared/lib/date"
import { paths } from "@/shared/lib/paths"
import { joinClasses } from "@/shared/lib/utils"

import { getMyPosting, saveMemo } from "../api"
import { DdayBadge } from "../components/my-bits"
import { dueText, statusDetail } from "../status"

export function myPostLoader({ params }: LoaderFunctionArgs) {
  return getMyPosting(params.postingId ?? "")
}

const sourceLink = "-my-3 inline-flex min-h-control-md items-center gap-1 font-bold whitespace-nowrap text-primary"

/** 공고 상세(시안 8차, 내 공고): 마감과 지원처, 내 상태, 공고 내용, 메모, 맞는 카드, 이 공고가 있는 그룹. */
export function MyPostPage() {
  const { today, posting } = useLoaderData<typeof myPostLoader>()
  const due = dueText(posting, today)
  const [memo, setMemo] = useState(posting.memo)
  // 저장한 값과 비교한다. loader의 posting.memo는 저장해도 다시 읽지 않아 처음 값에 머문다
  const lastSaved = useRef(posting.memo)
  const [saved, setSaved] = useState(false)
  const full = posting.deadline.kind === "date" ? `${formatDateWeekday(posting.deadline.date)} 마감` : due.sub

  return (
    <Page narrow className="gap-6 pt-10">
      <BackLink to={paths.myPosts()}>내 공고</BackLink>
      <div className="flex flex-col gap-3">
        <p className="text-sm text-text-secondary">{posting.company}</p>
        <Heading level={1} size="text-3xl">
          {posting.title}
        </Heading>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
          <DdayBadge text={due.main} soon={due.soon} />
          <span>{full}</span>
          {posting.applyAt && <span className="text-text-secondary">지원하는 곳: {posting.applyAt}</span>}
          {posting.sourceUrl ? (
            <a href={posting.sourceUrl} target="_blank" rel="noreferrer" className={sourceLink}>
              <span>원문 보기</span>
              <ExternalLinkIcon aria-hidden="true" className="size-4" />
            </a>
          ) : (
            // shortcut: 원문 주소가 없으면 눌러도 아무 일이 없는 자리만 둔다, 서버를 붙일 때 주소 없는 공고에서 감출지 정한다
            <PlaceholderLink className={sourceLink}>
              <span>원문 보기</span>
              <ExternalLinkIcon aria-hidden="true" className="size-4" />
            </PlaceholderLink>
          )}
        </div>
      </div>

      <div className={columns}>
        <div className={joinClasses(mainColumn, "gap-6")}>
          <Panel aria-labelledby="mp-state" className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3 p-6">
            <div className="flex flex-col gap-1">
              <h2 id="mp-state" className={subLabel}>
                내 상태
              </h2>
              <p className="text-lg font-bold">{statusDetail(posting)}</p>
            </div>
            <Link to={paths.board(posting.id)} className={buttonVariants({ variant: "outline" })}>
              상태 바꾸기
            </Link>
          </Panel>

          <Panel aria-labelledby="mp-body" className="flex flex-col gap-5 p-6">
            <h2 id="mp-body" className={sectionTitle}>
              공고 내용
            </h2>
            {posting.sections.length === 0 && posting.keywords.length === 0 && <p className="text-sm text-text-secondary">아직 정리된 공고 내용이 없습니다.</p>}
            {posting.sections.map((s) => (
              <div key={s.title} className="flex flex-col gap-2">
                <h3 className={subLabel}>{s.title}</h3>
                <ul className="flex flex-col gap-1.5">
                  {s.items.map((item) => (
                    <li key={item} className="flex items-start gap-2.5">
                      <Dot tone="muted" className="mt-[9px]" />
                      <span>{item}</span>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
            {posting.keywords.length > 0 && (
              <div className="flex flex-col gap-2">
                <h3 className={subLabel}>키워드</h3>
                <ul className="flex flex-wrap gap-1.5">
                  {posting.keywords.map((k) => (
                    <Chip key={k}>{k}</Chip>
                  ))}
                </ul>
              </div>
            )}
          </Panel>

          <Panel aria-labelledby="mp-memo" className="flex flex-col gap-4 p-6">
            <div className="flex flex-col gap-1">
              <h2 id="mp-memo" className={sectionTitle}>
                나만 보는 메모
              </h2>
              <p className="text-sm text-text-secondary">그룹에는 보이지 않습니다.</p>
            </div>
            <label htmlFor="mp-memo-text" className="sr-only">
              메모
            </label>
            {/* 칸을 떠나면 저장한다(시안에 저장 버튼이 없다) */}
            <Textarea
              id="mp-memo-text"
              rows={3}
              value={memo}
              onChange={(e) => {
                setMemo(e.target.value)
                setSaved(false)
              }}
              onBlur={async () => {
                if (memo === lastSaved.current) return
                await saveMemo(posting.id, memo)
                lastSaved.current = memo
                setSaved(true)
              }}
              className="min-h-24"
            />
            <p role="status" className={joinClasses("text-label-sm text-text-secondary", !saved && "sr-only")}>
              {saved ? "메모를 저장했습니다." : ""}
            </p>
          </Panel>
        </div>

        <aside className={joinClasses(sideColumn(320), "gap-6")}>
          <Panel aria-labelledby="mp-match" className="flex flex-col gap-4 p-6">
            <div className="flex flex-col gap-1">
              <h2 id="mp-match" className={sectionTitle}>
                맞는 카드
              </h2>
              <p className="text-sm text-text-secondary">공고의 요건·키워드와 카드의 태그가 겹치는 순서입니다.</p>
            </div>
            {posting.matches.length > 0 ? (
              <ul className="flex flex-col">
                {posting.matches.map((m, i) => (
                  <li key={m.cardId} className={joinClasses("border-t py-3", i > 0 ? "border-border-subtle" : "border-transparent")}>
                    <Link to={paths.card(m.cardId)} className="flex min-w-0 flex-col gap-1.5 text-foreground">
                      <span className="text-sm font-bold">{m.title}</span>
                      <span className="text-label-sm text-text-secondary">겹치는 것: {m.overlap.join(", ")}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-text-secondary">겹치는 카드가 없습니다.</p>
            )}
            {posting.unmatched.length > 0 && (
              <PanelNote className="pt-3">카드로 답하지 못한 요건: {posting.unmatched.join(", ")}</PanelNote>
            )}
          </Panel>

          <Panel aria-labelledby="mp-groups" className="flex flex-col gap-4 p-6">
            <h2 id="mp-groups" className={sectionTitle}>
              이 공고가 있는 그룹
            </h2>
            {posting.groupStates.length > 0 ? (
              posting.groupStates.map((g) => (
                <Link key={g.id} to={paths.groupPost(g.id, g.postId)} className="flex min-h-13 items-center gap-3 text-foreground">
                  <Avatar size={32} tone="accent">
                    {g.mark}
                  </Avatar>
                  <span className="flex min-w-0 flex-col">
                    <span className="text-sm font-bold">{g.name}</span>
                    <span className="text-label-sm text-text-secondary">
                      {g.interested ? "관심 켬" : "관심 끔"} · 댓글 {g.commentCount} · 공개 범위: {DISCLOSURE_LABELS[g.disclosure]}
                    </span>
                  </span>
                </Link>
              ))
            ) : (
              <p className="text-sm text-text-secondary">이 공고가 있는 내 그룹이 없습니다.</p>
            )}
          </Panel>
        </aside>
      </div>
    </Page>
  )
}
