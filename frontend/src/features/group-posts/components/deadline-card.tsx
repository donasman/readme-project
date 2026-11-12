import { CheckIcon, ExternalLinkIcon } from "@/components/icons"
import { PlaceholderLink } from "@/components/placeholder-link"
import { Button } from "@/components/ui/button"
import { deadlineText } from "@/features/group-posts/deadline"
import type { GroupPostDetail } from "@/features/group-posts/types"

import { StatusPill } from "./chips"

interface DeadlineCardProps {
  post: GroupPostDetail
  today: string
  onToggleInterest: (interested: boolean) => void
}

const sourceLink =
  "inline-flex min-h-control-md flex-none items-center gap-1.5 rounded-full px-4 text-sm font-bold whitespace-nowrap text-highlight-foreground underline underline-offset-4"

/** 상세 맨 위의 하이라이트 카드: 마감(강조색 상자), 회사와 공고명, 관심 켜기·끄기, 원문 보기. */
export function DeadlineCard({ post, today, onToggleInterest }: DeadlineCardProps) {
  const due = deadlineText(post.deadline, today)
  const unknown = post.deadline.kind === "unknown"

  return (
    <article className="flex flex-wrap overflow-hidden rounded-surface bg-highlight text-highlight-foreground shadow-highlight-resting">
      <div className="flex min-w-0 flex-[1_1_220px] p-4">
        <div className="flex min-h-46 flex-1 flex-col justify-between gap-6 rounded-panel bg-primary p-6 text-primary-foreground">
          <span className="text-label-sm font-bold">{post.deadlineName ?? "마감"}</span>
          <div className="flex flex-col gap-1">
            {unknown ? (
              <span className="font-heading text-3xl leading-display font-bold tracking-display">{due.main}</span>
            ) : (
              <span className="font-heading text-5xl leading-none font-bold tracking-display">{due.main}</span>
            )}
            {due.sub && <span className="text-sm">{due.sub}</span>}
          </div>
        </div>
      </div>

      <div className="flex min-w-0 flex-[2_1_320px] flex-col items-start justify-center gap-2 px-6 pt-4 pb-6">
        <p className="text-base font-bold">{post.company}</p>
        <h1 className="font-heading text-[clamp(var(--text-2xl),8cqw,var(--text-3xl))] leading-display font-bold tracking-display">
          {post.title}
        </h1>
        {post.applyAt && <p className="text-sm">지원처 · {post.applyAt}</p>}
        <div className="mt-3 flex flex-wrap items-center gap-2">
          {post.me.status ? (
            <StatusPill>{post.me.status}</StatusPill>
          ) : post.me.interested ? (
            <Button
              aria-pressed="true"
              className="bg-surface text-foreground shadow-resting hover:bg-surface/90"
              onClick={() => onToggleInterest(false)}
            >
              <CheckIcon data-icon="inline-start" aria-hidden="true" />
              관심 중
            </Button>
          ) : (
            <Button aria-pressed="false" onClick={() => onToggleInterest(true)}>
              관심 켜기
            </Button>
          )}
          {post.sourceUrl ? (
            <a href={post.sourceUrl} target="_blank" rel="noreferrer" className={sourceLink}>
              <span>원문 보기</span>
              <ExternalLinkIcon aria-hidden="true" className="size-4" />
            </a>
          ) : (
            // 예시 데이터에 원문 주소가 없다: 자리만 둔다.
            <PlaceholderLink className={sourceLink}>
              <span>원문 보기</span>
              <ExternalLinkIcon aria-hidden="true" className="size-4" />
            </PlaceholderLink>
          )}
        </div>
        <p className="mt-1 text-label-sm">
          관심을 켜면 이 그룹 멤버에게 내 이름이 보이고, 이 공고가 내 공고 목록에 들어갑니다.
        </p>
      </div>
    </article>
  )
}
