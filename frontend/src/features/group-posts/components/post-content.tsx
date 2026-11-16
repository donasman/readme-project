import { Button } from "@/components/ui/button"
import { memberName } from "@/domain/group/member"
import type { Viewer } from "@/domain/session/types"
import { Chip } from "@/shared/components/chip"
import { Heading } from "@/shared/components/heading"
import { SurfaceCard } from "@/shared/components/surface-card"
import { formatMonthDay } from "@/shared/lib/date"
import { subjectParticle } from "@/shared/lib/korean"

import type { GroupPostDetail } from "../types"

interface PostContentProps {
  post: GroupPostDetail
  viewer: Viewer
  /** 올린 사람일 때만 "공고 고치기"가 보인다(그룹장도 못 고친다) */
  canEdit: boolean
  onEdit: () => void
  /** 올린 사람이나 그룹장일 때만 "그룹에서 내리기"가 보인다 */
  canTakeDown: boolean
  onTakeDown: () => void
}

/** 공고 내용: 올린 사람과 날, 주요 업무 등 묶음, 키워드. */
export function PostContent({ post, viewer, canEdit, onEdit, canTakeDown, onTakeDown }: PostContentProps) {
  const byMe = post.postedBy?.id === viewer.id
  const poster = byMe ? "내가" : `${memberName(post.postedBy)}${subjectParticle(memberName(post.postedBy))}`
  const empty = post.sections.length === 0 && post.keywords.length === 0

  return (
    <SurfaceCard aria-label="공고 내용" className="flex flex-col gap-5 p-6">
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div className="flex min-w-0 flex-col gap-1">
          <Heading>공고 내용</Heading>
          <p className="text-label-sm text-text-secondary">
            {poster} {formatMonthDay(post.postedAt)}에 올림
          </p>
        </div>
        {(canEdit || canTakeDown) && (
          <div className="flex flex-wrap items-center gap-2">
            {canEdit && (
              <Button size="sm" variant="outline" className="flex-none" onClick={onEdit}>
                공고 고치기
              </Button>
            )}
            {canTakeDown && (
              <Button size="sm" variant="destructive" className="flex-none text-foreground dark:bg-destructive/10" onClick={onTakeDown}>
                그룹에서 내리기
              </Button>
            )}
          </div>
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
                  <Chip key={keyword}>{keyword}</Chip>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </SurfaceCard>
  )
}
