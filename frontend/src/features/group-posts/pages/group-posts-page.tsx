import { useMemo, useState } from "react"
import { Link, useLoaderData, useLocation, useRevalidator, type LoaderFunctionArgs } from "react-router"

import { buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useGroup } from "@/domain/group/use-group"
import type { FillDeadlineState } from "@/domain/posting/types"
import { useViewer } from "@/domain/session/use-viewer"
import { Page } from "@/shared/components/page"
import { SurfaceCard } from "@/shared/components/surface-card"
import { PlusIcon, SearchIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { clearRouterState, useSearchText } from "@/shared/lib/router"
import { docTitle } from "@/shared/lib/utils"

import { getGroupPosts, setDeadline } from "../api"
import { AppliedBadge } from "../components/chips"
import { ListToolbar } from "../components/list-toolbar"
import { DeadlineDialog } from "../components/post-dialogs"
import { POST_GRID, PostRow } from "../components/post-row"
import { buildPostList, knownMember } from "../list-rules"
import { useInterest } from "../use-interest"
import type { GroupPost } from "../types"
import { useListFilters } from "../use-list-filters"

export function groupPostsLoader({ params }: LoaderFunctionArgs) {
  return getGroupPosts(params.groupId ?? "")
}

/** 그룹 공고 목록(docs/design/group-posts.dc.html). */
export function GroupPostsPage() {
  const { today, posts } = useLoaderData<typeof groupPostsLoader>()
  const viewer = useViewer()
  const { group } = useGroup()
  const [urlFilters, setFilters] = useListFilters()
  const [query, setQuery] = useSearchText(urlFilters.query)
  const interest = useInterest(group.id)
  const revalidator = useRevalidator()
  const location = useLocation()
  // 홈의 "마감일을 못 읽었습니다"로 오면 그 공고의 마감일 넣기 창을 바로 연다
  const [fillFor, setFillFor] = useState<GroupPost | null>(
    () => posts.find((p) => p.id === (location.state as FillDeadlineState | null)?.fillDeadline && p.deadline.kind === "unknown") ?? null
  )
  const closeFill = () => {
    setFillFor(null)
    if (location.state) clearRouterState()
  }

  const others = group.members.filter((m) => m.id !== viewer.id)
  const member = knownMember(urlFilters.member, others)
  const filters = useMemo(() => ({ ...urlFilters, member }), [urlFilters, member])
  const list = useMemo(
    () => buildPostList(posts.map(interest.apply), filters, today),
    [posts, interest.apply, filters, today]
  )

  return (
    <Page className="gap-6 pt-6">
      <title>{docTitle("공고", group.name)}</title>
      {/* 그룹 이름과 공고·멤버 탭은 위의 그룹 머리 영역이 보인다. 제목은 화면 읽기에만 둔다 */}
      <h1 className="sr-only">공고</h1>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="relative flex max-w-80 min-w-0 flex-[1_1_200px] items-center">
          <label htmlFor="post-search" className="sr-only">
            회사명, 공고명 검색
          </label>
          <SearchIcon aria-hidden="true" className="pointer-events-none absolute left-3.5 size-4 text-text-secondary" />
          <Input
            id="post-search"
            type="search"
            placeholder="회사명, 공고명"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
              setFilters({ query: event.target.value })
            }}
            className="pl-10 md:text-base"
          />
        </div>
        <Link to={paths.groupPostNew(group.id)} className={buttonVariants({ className: "flex-none" })}>
          <PlusIcon data-icon="inline-start" aria-hidden="true" />
          공고 올리기
        </Link>
      </div>

      <ListToolbar filters={filters} counts={list.counts} members={others} onChange={setFilters} />

      <SurfaceCard aria-label="공고 목록" className="px-6 pt-2 pb-4">
        <div
          aria-hidden="true"
          className={`hidden items-center gap-4 pt-3 pb-2.5 text-xs font-semibold text-text-secondary md:grid ${POST_GRID}`}
        >
          <span>마감</span>
          <span>공고</span>
          <span className="inline-flex items-center gap-1.5">
            <span>관심 멤버</span>
            <AppliedBadge className="pr-2">지원함</AppliedBadge>
          </span>
          <span>내 상태</span>
          <span>댓글</span>
          <span>올린 사람</span>
        </div>

        {list.sections.map((section, index) => (
          <div key={section.title || `rows-${index}`}>
            {section.title && (
              <h2 className="flex items-center gap-2 border-t border-border-subtle pt-3.5 pb-2.5 text-label-sm font-bold">
                <span>{section.title}</span>
                <span className="font-medium text-text-secondary">{section.rows.length}</span>
              </h2>
            )}
            <ul>
              {section.rows.map((row) => (
                <PostRow
                  key={row.post.id}
                  row={row}
                  today={today}
                  viewer={viewer}
                  detailPath={paths.groupPost(group.id, row.post.id)}
                  onToggleInterest={(on) => void interest.toggle(row.post.id, on)}
                  onFillDeadline={() => setFillFor(row.post)}
                />
              ))}
            </ul>
          </div>
        ))}

        {list.sections.length === 0 && (
          <p className="border-t border-border-subtle pt-10 pb-7 text-center text-sm text-text-secondary">
            조건에 맞는 공고가 없습니다.
          </p>
        )}
        <p role="status" className="sr-only">
          공고 {list.sections.reduce((n, s) => n + s.rows.length, 0)}개
        </p>
      </SurfaceCard>

      <DeadlineDialog
        target={fillFor}
        onClose={closeFill}
        onSave={async (deadline) => {
          const saved = fillFor ? await setDeadline(group.id, fillFor.id, deadline) : true
          // 먼저 넣은 마감도 목록에 보이게 다시 읽는다. 저장했으면 창을 닫고, 아니면 창이 그 사실을 알린다
          await revalidator.revalidate()
          if (saved) closeFill()
          return saved
        }}
      />
    </Page>
  )
}
