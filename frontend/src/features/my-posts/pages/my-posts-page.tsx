import { Link, useLoaderData } from "react-router"

import SegmentedControl from "@/components/arc/segmented-control/segmented-control"
import { buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { PostingLink } from "@/domain/posting/posting-link"
import { DECISION_LABELS } from "@/domain/posting/my-state"
import { Heading } from "@/shared/components/heading"
import { MySpaceNav } from "@/shared/components/my-space-nav"
import { Page } from "@/shared/components/page"
import { pageHead } from "@/shared/components/page-layout"
import { Panel } from "@/shared/components/panel"
import { ToggleButton } from "@/shared/components/toggle-button"
import { PlusIcon, SearchIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { useSearchText } from "@/shared/lib/router"

import { getMyPostings } from "../api"
import { MyStatusPill } from "../components/my-bits"
import { dueText, filterPostings, type MyFilters } from "../status"
import type { Decision } from "../types"
import { useMyFilters } from "../use-my-filters"

export function myPostsLoader() {
  return getMyPostings()
}

const CHIPS: (Decision | "all")[] = ["all", "undecided", "hold", "applied", "skip"]

/** 넓으면 칸 넷(마감, 회사·공고, 내 상태, 그룹), 768px 미만이면 카드(회사·공고와 마감 / 그룹 / 내 상태). */
const ROW_GRID =
  "grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-2.5 [grid-template-areas:'post_due'_'fans_cmt'_'me_me'] md:grid-cols-[120px_minmax(0,1fr)_170px_160px] md:gap-4 md:[grid-template-areas:'due_post_me_fans']"

/** 내 공고(시안 8차): 관심을 켜거나 직접 넣은 공고와 내 상태. 진행 중·마감됨, 결정, 검색으로 거른다. */
export function MyPostsPage() {
  const { today, postings } = useLoaderData<typeof myPostsLoader>()
  const [filters, setFilters] = useMyFilters()
  const [query, setQuery] = useSearchText(filters.query)
  const { rows, open, closed } = filterPostings(postings, filters, today)

  return (
    <Page narrow className="gap-6 pt-10">
      <MySpaceNav current="posts" />
      <div className={pageHead}>
        <div className="flex min-w-0 flex-[1_1_360px] flex-col gap-2">
          <Heading level={1} size="text-3xl">
            내 공고
          </Heading>
          <p className="text-text-secondary">관심을 켜거나 직접 넣은 공고입니다. 지원 상태는 여기 값이 원본이고, 그룹에는 정한 공개 범위만큼 보입니다.</p>
        </div>
        <div className="flex min-w-0 flex-[1_1_320px] flex-wrap items-center justify-end gap-2">
          <div className="relative flex max-w-80 min-w-0 flex-[1_1_200px] items-center">
            <label htmlFor="my-search" className="sr-only">
              회사명, 공고명 검색
            </label>
            <SearchIcon aria-hidden="true" className="pointer-events-none absolute left-3.5 size-4 text-text-secondary" />
            <Input
              id="my-search"
              type="search"
              placeholder="회사명, 공고명"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value)
                setFilters({ query: e.target.value })
              }}
              className="pl-10 md:text-base"
            />
          </div>
          <Link to={paths.myPostNew()} className={buttonVariants({ className: "flex-none" })}>
            <PlusIcon data-icon="inline-start" aria-hidden="true" />
            공고 넣기
          </Link>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <SegmentedControl
          label="진행 여부"
          value={filters.view}
          onValueChange={(view) => setFilters({ view: view as MyFilters["view"] })}
          options={[
            { value: "open", label: "진행 중", accessory: <span className="font-medium">{open}</span> },
            { value: "closed", label: "마감됨", accessory: <span className="font-medium">{closed}</span> },
          ]}
        />
        <div role="group" aria-label="결정으로 거르기" className="flex min-w-0 items-center gap-2 overflow-x-auto [scrollbar-width:none]">
          {CHIPS.map((d) => (
            <ToggleButton key={d} pressed={filters.decision === d} className="flex-none" onClick={() => setFilters({ decision: d })}>
              {d === "all" ? "전체" : DECISION_LABELS[d]}
            </ToggleButton>
          ))}
        </div>
      </div>

      <Panel as="ul" aria-label="내 공고 목록" className="flex flex-col">
        <li aria-hidden="true" className={`hidden items-center px-5 py-3 text-xs font-bold text-text-secondary md:grid ${ROW_GRID}`}>
          <span>마감</span>
          <span>회사 · 공고</span>
          <span>내 상태</span>
          <span>그룹</span>
        </li>
        {rows.map((p) => {
          const due = dueText(p, today)
          return (
            <li key={p.id} className={`items-center border-t border-border-subtle px-5 py-4 ${ROW_GRID}`}>
              <div className="flex flex-col items-end gap-0.5 text-right [grid-area:due] md:items-start md:text-left">
                <span className="font-bold whitespace-nowrap">{due.main}</span>
                <span className="text-xs whitespace-nowrap text-text-secondary">{due.sub}</span>
              </div>
              <PostingLink id={p.id} company={p.company} title={p.title} className="[grid-area:post]" />
              <div className="flex items-center [grid-area:me]">
                <MyStatusPill state={p} />
              </div>
              <span className="text-label-sm text-text-secondary [grid-area:fans]">{p.groups.map((g) => g.name).join(", ")}</span>
            </li>
          )
        })}
      </Panel>
      {rows.length === 0 && <p className="text-sm text-text-secondary">이 조건에 맞는 공고가 없습니다.</p>}
    </Page>
  )
}
