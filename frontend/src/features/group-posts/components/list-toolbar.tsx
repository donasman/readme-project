import SegmentedControl from "@/components/arc/segmented-control/segmented-control"
import { CheckIcon, ChevronDownIcon } from "@/shared/icons"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { SORT_LABELS, type ListFilters, type Sort, type Tab } from "@/features/group-posts/list-rules"
import type { Member } from "@/features/group-posts/types"

interface ListToolbarProps {
  filters: ListFilters
  counts: Record<Tab, number>
  /** 보는 사람을 뺀 그룹 멤버 */
  members: Member[]
  onChange: (patch: Partial<ListFilters>) => void
}

// 멤버 메뉴의 라디오 값. "" 는 라디오 값으로 쓰기 어려워 "all" 로 바꿔 둔다.
const ALL = "all"

/** 진행 중·마감됨, 내 관심만, 멤버 선택, 정렬. 세그먼트(42px) 옆에는 sm 버튼(36px)을 둔다. */
export function ListToolbar({ filters, counts, members, onChange }: ListToolbarProps) {
  const memberName =
    filters.member === "" ? "전체" : filters.member === "me" ? "나" : (members.find((m) => m.id === filters.member)?.name ?? "전체")

  return (
    <div className="relative z-10 flex flex-wrap items-center gap-x-3 gap-y-2">
      <SegmentedControl
        label="진행 여부"
        value={filters.tab}
        onValueChange={(tab) => onChange({ tab: tab as Tab })}
        options={[
          { value: "open", label: "진행 중", accessory: <Count n={counts.open} /> },
          { value: "closed", label: "마감됨", accessory: <Count n={counts.closed} /> },
        ]}
      />

      {filters.mine ? (
        <Button variant="highlight" size="sm" aria-pressed="true" onClick={() => onChange({ mine: false })}>
          <CheckIcon data-icon="inline-start" aria-hidden="true" />
          내 관심만
        </Button>
      ) : (
        <Button variant="outline" size="sm" aria-pressed="false" onClick={() => onChange({ mine: true })}>
          내 관심만
        </Button>
      )}

      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="outline" size="sm" />}>
          멤버: {memberName}
          <ChevronDownIcon data-icon="inline-end" aria-hidden="true" />
        </DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuGroup>
            <DropdownMenuLabel>이 멤버가 관심을 켠 공고</DropdownMenuLabel>
            <DropdownMenuRadioGroup
              value={filters.member || ALL}
              onValueChange={(value: string) => onChange({ member: value === ALL ? "" : value })}
            >
              <DropdownMenuRadioItem value={ALL}>전체</DropdownMenuRadioItem>
              <DropdownMenuRadioItem value="me">나</DropdownMenuRadioItem>
              {members.map((m) => (
                <DropdownMenuRadioItem key={m.id} value={m.id}>
                  {m.name}
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
          </DropdownMenuGroup>
        </DropdownMenuContent>
      </DropdownMenu>

      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="outline" size="sm" className="ml-auto" />}>
          {SORT_LABELS[filters.sort]}
          <ChevronDownIcon data-icon="inline-end" aria-hidden="true" />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuRadioGroup value={filters.sort} onValueChange={(sort: string) => onChange({ sort: sort as Sort })}>
            {(Object.keys(SORT_LABELS) as Sort[]).map((sort) => (
              <DropdownMenuRadioItem key={sort} value={sort}>
                {SORT_LABELS[sort]}
              </DropdownMenuRadioItem>
            ))}
          </DropdownMenuRadioGroup>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  )
}

function Count({ n }: { n: number }) {
  return <span className="font-medium text-text-secondary">{n}</span>
}
