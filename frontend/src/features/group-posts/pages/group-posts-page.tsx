import { useMemo } from "react"
import { useLoaderData, type LoaderFunctionArgs } from "react-router"

import { CheckIcon, PlusIcon, SearchIcon } from "@/shared/icons"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useGroup } from "@/domain/group/use-group"
import { useViewer } from "@/domain/session/use-viewer"
import { getGroupPosts } from "@/features/group-posts/api"
import { ListToolbar } from "@/features/group-posts/components/list-toolbar"
import { POST_GRID, PostRow } from "@/features/group-posts/components/post-row"
import { buildPostList } from "@/features/group-posts/list-rules"
import { useInterest } from "@/features/group-posts/use-interest"
import { useListFilters } from "@/features/group-posts/use-list-filters"
import { paths } from "@/shared/lib/paths"

export function groupPostsLoader({ params }: LoaderFunctionArgs) {
  return getGroupPosts(params.groupId ?? "")
}

/** 그룹 공고 목록(docs/design/group-posts.dc.html). */
export function GroupPostsPage() {
  const { today, posts } = useLoaderData<typeof groupPostsLoader>()
  const viewer = useViewer()
  const { group } = useGroup()
  const [filters, setFilters] = useListFilters()
  const interest = useInterest(group.id)

  const list = useMemo(
    () => buildPostList(posts.map(interest.apply), filters, today),
    [posts, interest.apply, filters, today]
  )
  const others = group.members.filter((m) => m.id !== viewer.id)

  return (
    <main className="mx-auto flex max-w-[1240px] flex-col gap-6 px-4 pt-10 pb-16">
      <div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-4">
        <div className="flex min-w-0 flex-col gap-1">
          <p className="text-sm text-text-secondary">
            {group.name} · 멤버 {group.members.length}명
          </p>
          <h1 className="font-heading text-[clamp(2.5rem,10cqw,var(--text-5xl))] leading-display font-bold tracking-display">
            공고
          </h1>
        </div>
        <div className="flex min-w-0 flex-[1_1_320px] flex-wrap items-center justify-end gap-2">
          <div className="relative flex max-w-80 min-w-0 flex-[1_1_200px] items-center">
            <label htmlFor="post-search" className="sr-only">
              회사명, 공고명 검색
            </label>
            <SearchIcon aria-hidden="true" className="pointer-events-none absolute left-3.5 size-4 text-text-secondary" />
            <Input
              id="post-search"
              type="search"
              placeholder="회사명, 공고명"
              value={filters.query}
              onChange={(event) => setFilters({ query: event.target.value })}
              className="pl-10 md:text-base"
            />
          </div>
          {/* 공고 올리기: 아직 동작 없음 */}
          <Button className="flex-none">
            <PlusIcon data-icon="inline-start" aria-hidden="true" />
            공고 올리기
          </Button>
        </div>
      </div>

      <ListToolbar filters={filters} counts={list.counts} members={others} onChange={setFilters} />

      <section aria-label="공고 목록" className="rounded-surface border border-border bg-surface px-6 pt-2 pb-4 shadow-resting">
        <div
          aria-hidden="true"
          className={`hidden items-center gap-4 pt-3 pb-2.5 text-xs font-semibold text-text-secondary md:grid ${POST_GRID}`}
        >
          <span>마감</span>
          <span>공고</span>
          <span className="inline-flex items-center gap-1.5">
            <span>관심 멤버</span>
            <span className="inline-flex items-center gap-0.5 rounded-full bg-accent-subtle pr-2 pl-1.5 text-accent-strong">
              <CheckIcon className="size-3" strokeWidth={2.5} />
              <span>지원함</span>
            </span>
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
      </section>
    </main>
  )
}
