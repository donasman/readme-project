import { Link, useLocation } from "react-router"

import { Button } from "@/components/ui/button"
import { memberName } from "@/domain/group/member"
import type { Viewer } from "@/domain/session/types"
import { Pill } from "@/shared/components/pill"
import { CheckIcon, MessageSquareIcon } from "@/shared/icons"
import { formatMonthDay } from "@/shared/lib/date"

import { deadlineText } from "@/domain/posting/deadline"
import type { PostRow as Row } from "../list-rules"
import type { ListReturnState } from "../types"
import { MemberChip, NewBadge, StatusPill } from "./chips"

/** 칸 6개: 마감, 공고, 관심 멤버, 내 상태, 댓글, 올린 사람. 768px 미만에서는 카드가 된다. */
export const POST_GRID = "md:grid-cols-[104px_minmax(0,1.15fr)_minmax(0,1fr)_128px_56px_132px]"

interface PostRowProps {
  row: Row
  today: string
  viewer: Viewer
  detailPath: string
  onToggleInterest: (interested: boolean) => void
  /** 마감을 못 읽은 공고의 "날짜 넣기" */
  onFillDeadline: () => void
}

/**
 * 목록 한 줄. 넓으면 칸 6개, 768px 미만이면 카드(회사명과 D-day / 관심 멤버와 댓글 수 / 내 상태)이고
 * 올린 사람은 상세에서 보여 준다(docs/design/readme-tabs.css 의 rm-row 규칙).
 */
export function PostRow({ row, today, viewer, detailPath, onToggleInterest, onFillDeadline }: PostRowProps) {
  const back: ListReturnState = { listSearch: useLocation().search }
  const { post } = row
  const due = deadlineText(post.deadline, today)

  return (
    <li
      className={`grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 gap-y-2.5 border-t border-border-subtle py-3.5 md:gap-4 ${POST_GRID}`}
    >
      <div className="col-start-2 row-start-1 flex min-w-0 flex-col items-end gap-0.5 text-right md:col-start-1 md:items-stretch md:text-left">
        {post.deadline.kind === "unknown" ? (
          <>
            <span className="text-label-sm font-bold text-warning">마감일 확인 필요</span>
            <Button variant="outline" size="xs" className="mt-0.5 h-7 self-start px-3" onClick={onFillDeadline}>
              날짜 넣기
            </Button>
          </>
        ) : (
          <>
            <span className="text-base font-bold whitespace-nowrap">{due.main}</span>
            <span className="text-xs whitespace-nowrap text-text-secondary">{due.sub}</span>
          </>
        )}
      </div>

      <Link
        to={detailPath}
        state={back}
        className="col-start-1 row-start-1 flex min-w-0 flex-col gap-0.5 text-foreground md:col-start-2"
      >
        <span className="flex min-w-0 items-center gap-2">
          <span className="truncate text-base font-bold">{post.company}</span>
          {row.isNew && <NewBadge />}
        </span>
        <span className="truncate text-sm">{post.title}</span>
      </Link>

      <div className="col-start-1 row-start-2 flex min-w-0 flex-wrap items-center gap-1 md:col-start-3 md:row-start-1">
        {row.fans.map((fan) =>
          fan.kind === "me" ? (
            <Pill key="me" tone="highlight">나</Pill>
          ) : (
            <MemberChip key={fan.member.id} name={fan.member.name} applied={fan.applied} />
          )
        )}
        {row.fans.length === 0 && <span className="text-label-sm text-text-secondary">아직 없음</span>}
      </div>

      <div className="col-span-2 row-start-3 flex min-w-0 items-center md:col-span-1 md:col-start-4 md:row-start-1">
        {post.me.status ? (
          <StatusPill>{post.me.status}</StatusPill>
        ) : post.me.interested ? (
          <Button variant="highlight" size="sm" aria-pressed="true" onClick={() => onToggleInterest(false)}>
            <CheckIcon data-icon="inline-start" aria-hidden="true" />
            관심 중
          </Button>
        ) : (
          <Button variant="outline" size="sm" aria-pressed="false" onClick={() => onToggleInterest(true)}>
            관심 켜기
          </Button>
        )}
      </div>

      <Link
        to={`${detailPath}#comments`}
        state={back}
        aria-label={`댓글 ${post.commentCount}개`}
        className="col-start-2 row-start-2 inline-flex min-h-control-sm items-center gap-1 justify-self-end text-sm text-text-secondary md:col-start-5 md:row-start-1 md:justify-self-auto"
      >
        <MessageSquareIcon aria-hidden="true" className="size-4" />
        <span>{post.commentCount}</span>
      </Link>

      <div className="hidden min-w-0 text-label-sm text-text-secondary md:col-start-6 md:row-start-1 md:block">
        {post.postedBy?.id === viewer.id ? "나" : memberName(post.postedBy)} · {formatMonthDay(post.postedAt)}
      </div>
    </li>
  )
}
