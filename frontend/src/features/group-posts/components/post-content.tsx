import { useState } from "react"

import SegmentedControl from "@/components/arc/segmented-control/segmented-control"
import { Button } from "@/components/ui/button"
import { memberName } from "@/domain/group/member"
import { CONTENT_VIEWS, formatQuestionLimit, summarySections, type ContentView } from "@/domain/posting/analysis"
import type { Viewer } from "@/domain/session/types"
import { ArrowLink } from "@/shared/components/arrow-link"
import { Chip } from "@/shared/components/chip"
import { Heading } from "@/shared/components/heading"
import { SurfaceCard } from "@/shared/components/surface-card"
import { formatMonthDay } from "@/shared/lib/date"
import { paths } from "@/shared/lib/paths"
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

/**
 * 공고 내용: 올린 사람과 날, 주요 업무 등 묶음, 키워드. 공고 분석이 있으면 요약(12차 시안 안 A)과 원문을 고른다.
 * 맞는 카드는 내 카드 기준이라 내 공고 상세에만 있다. 관심을 켠 공고면 그리로 가는 링크를 둔다.
 */
export function PostContent({ post, viewer, canEdit, onEdit, canTakeDown, onTakeDown }: PostContentProps) {
  const [view, setView] = useState<ContentView>("summary")
  const byMe = post.postedBy?.id === viewer.id
  const poster = byMe ? "내가" : `${memberName(post.postedBy)}${subjectParticle(memberName(post.postedBy))}`
  const summary = view === "summary" ? post.analysis : null
  const shown = summary ? { sections: summarySections(summary), keywords: summary.keywords } : post
  const empty = shown.sections.length === 0 && shown.keywords.length === 0

  return (
    <SurfaceCard aria-label="공고 내용" className="flex flex-col gap-5 p-6">
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div className="flex min-w-0 flex-col gap-1">
          <Heading>공고 내용</Heading>
          <p className="text-label-sm text-text-secondary">
            {poster} {formatMonthDay(post.postedAt)}에 올림
          </p>
        </div>
        {post.analysis && <SegmentedControl label="보는 방식" value={view} onValueChange={(v) => setView(v as ContentView)} options={CONTENT_VIEWS} />}
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
          {shown.sections.map((section) => (
            <div key={section.title} className="flex flex-col gap-2">
              <h3 className="text-sm font-bold">{section.title}</h3>
              <ul className="flex list-disc flex-col gap-1.5 pl-4.5 text-sm">
                {section.items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
          ))}
          {shown.keywords.length > 0 && (
            <div className="flex flex-col gap-2">
              <h3 className="text-sm font-bold">키워드</h3>
              <ul className="flex flex-wrap gap-1.5">
                {shown.keywords.map((keyword) => (
                  <Chip key={keyword}>{keyword}</Chip>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
      {summary && summary.questions.length > 0 && (
        <div className="flex flex-col gap-2 border-t border-border-subtle pt-5">
          <h3 className="text-sm font-bold">자기소개서 문항</h3>
          <ol className="flex flex-col gap-2.5 text-sm">
            {summary.questions.map((q) => (
              <li key={q.text} className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                <span className="min-w-0 flex-[1_1_260px]">{q.text}</span>
                <span className="flex-none text-[13px] font-bold whitespace-nowrap">{formatQuestionLimit(q.limit)}</span>
              </li>
            ))}
          </ol>
        </div>
      )}
      {summary && <p className="text-label-sm text-text-secondary">요약은 AI가 공고 내용을 줄인 것입니다. 지원하기 전에 원문을 확인하세요.</p>}
      {post.me.interested && (
        <ArrowLink to={paths.myPost(post.id)} className="self-start">
          맞는 카드는 내 공고에서 보기
        </ArrowLink>
      )}
    </SurfaceCard>
  )
}
