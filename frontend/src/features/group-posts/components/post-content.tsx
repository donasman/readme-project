import { Button } from "@/components/ui/button"
import type { Viewer } from "@/domain/session/types"
import { formatMonthDay } from "@/shared/lib/date"
import { subjectParticle } from "@/shared/lib/korean"

import type { GroupPostDetail } from "../types"

interface PostContentProps {
  post: GroupPostDetail
  viewer: Viewer
  /** 올린 사람이나 그룹장일 때만 "그룹에서 내리기"가 보인다 */
  canTakeDown: boolean
}

/** 공고 내용: 올린 사람과 날, 주요 업무 등 묶음, 키워드. */
export function PostContent({ post, viewer, canTakeDown }: PostContentProps) {
  const byMe = post.postedBy.id === viewer.id
  const poster = byMe ? "내가" : `${post.postedBy.name}${subjectParticle(post.postedBy.name)}`
  const empty = post.sections.length === 0 && post.keywords.length === 0

  return (
    <section
      aria-label="공고 내용"
      className="flex flex-col gap-5 rounded-surface border border-border bg-surface p-6 shadow-resting"
    >
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div className="flex min-w-0 flex-col gap-1">
          <h2 className="font-heading text-xl leading-display font-bold tracking-display">공고 내용</h2>
          <p className="text-label-sm text-text-secondary">
            {poster} {formatMonthDay(post.postedAt)}에 올림
          </p>
        </div>
        {canTakeDown && (
          // 그룹에서 내리기: 아직 동작 없음
          <Button size="sm" variant="destructive" className="flex-none text-foreground dark:bg-destructive/10">
            그룹에서 내리기
          </Button>
        )}
      </div>

      {empty ? (
        <p className="text-sm text-text-secondary">아직 정리된 공고 내용이 없습니다.</p>
      ) : (
        <div className="grid grid-cols-[repeat(auto-fit,minmax(min(280px,100%),1fr))] gap-x-8 gap-y-5">
          {post.sections.map((section) => (
            <div key={section.title} className="flex flex-col gap-2">
              <h3 className="text-sm font-bold">{section.title}</h3>
              <ul className="flex list-disc flex-col gap-1.5 pl-4.5 text-sm">
                {section.items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
          ))}
          {post.keywords.length > 0 && (
            <div className="flex flex-col gap-2">
              <h3 className="text-sm font-bold">키워드</h3>
              <ul className="flex flex-wrap gap-1.5">
                {post.keywords.map((keyword) => (
                  <li key={keyword} className="rounded-full bg-surface-muted px-2.5 py-1 text-xs font-medium">
                    {keyword}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </section>
  )
}
