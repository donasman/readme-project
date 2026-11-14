import { useState } from "react"
import { useLoaderData, useRevalidator, useSearchParams } from "react-router"

import SegmentedControl from "@/components/arc/segmented-control/segmented-control"
import { Button } from "@/components/ui/button"
import { getMyGroups } from "@/domain/group/api"
import { DISCLOSURE_LABELS } from "@/domain/group/disclosure"
import { PostingLink } from "@/domain/posting/posting-link"
import type { Group } from "@/domain/group/types"
import { Heading, sectionTitle } from "@/shared/components/heading"
import { MySpaceNav } from "@/shared/components/my-space-nav"
import { Page } from "@/shared/components/page"
import { columns, mainColumn, sideColumn } from "@/shared/components/page-layout"
import { Panel, PanelNote } from "@/shared/components/panel"
import { CheckIcon, XIcon } from "@/shared/icons"
import { joinClasses } from "@/shared/lib/utils"

import { getMyPostings, updateMyState } from "../api"
import { BigStat } from "../components/my-bits"
import { boardCounts, DECISION_LABELS, DECISIONS, dueText, RESULT_LABELS, RESULTS, sortBoard, STAGE_LABELS, STAGES, stageCells, withDecision } from "../status"
import type { Decision, MyPosting, MyState, Result, Stage } from "../types"

export async function boardLoader() {
  const [data, groups] = await Promise.all([getMyPostings(), getMyGroups()])
  return { ...data, groups }
}

const GRID = "grid grid-cols-[minmax(220px,1.6fr)_repeat(5,minmax(84px,1fr))_96px] items-center gap-2"

/** 지원 현황(시안 8차): 지원한 공고의 단계를 한 줄씩 보고, 옆 칸에서 상태를 바꾼다. 고른 공고는 주소의 ?posting=에 둔다. */
export function BoardPage() {
  const { today, postings, groups } = useLoaderData<typeof boardLoader>()
  const [params, setParams] = useSearchParams()
  const rows = sortBoard(postings)
  // ?posting=이 없을 때 처음 고른 첫 줄을 기억한다. 저장 뒤 다시 읽으면 줄 순서가 바뀌어 첫 줄이 다른 공고가 된다
  const [firstRow] = useState(rows[0]?.id)
  const selected = postings.find((p) => p.id === (params.get("posting") ?? firstRow)) ?? rows[0] ?? null
  const counts = boardCounts(postings)

  const pick = (id: string) => setParams({ posting: id }, { replace: true, preventScrollReset: true })

  return (
    <Page narrow className="gap-6 pt-10">
      <MySpaceNav current="board" />
      <div className="flex flex-col gap-2">
        <Heading level={1} size="text-3xl">
          지원 현황
        </Heading>
        <p className="text-text-secondary">지원한 공고가 어느 단계에 있는지 한 줄씩 봅니다.</p>
      </div>

      <dl className="grid grid-cols-[repeat(auto-fit,minmax(min(160px,100%),1fr))] gap-3">
        <BigStat label="지원함" value={counts.applied} />
        <BigStat label="진행 중" value={counts.active} />
        <BigStat label="최종 합격" value={counts.passed} />
        <BigStat label="불합격" value={counts.failed} />
      </dl>

      <div className={columns}>
        <section aria-label="지원한 공고" className={joinClasses(mainColumn, "gap-3")}>
          {rows.length > 0 ? (
            <Panel as="div" className="overflow-x-auto">
              <div className="flex min-w-[760px] flex-col">
                <div aria-hidden="true" className={`${GRID} px-5 py-3 text-xs font-bold text-text-secondary`}>
                  <span>회사 · 공고</span>
                  {STAGES.map((s) => (
                    <span key={s} className="text-center">
                      {STAGE_LABELS[s]}
                    </span>
                  ))}
                  <span />
                </div>
                {rows.map((p) => (
                  <div key={p.id} className={joinClasses(GRID, "border-t border-border-subtle px-5 py-3.5", p.id === selected?.id ? "bg-surface-muted" : "bg-transparent")}>
                    <PostingLink id={p.id} company={`${p.company} · ${dueText(p, today).main}`} title={p.title} />
                    {stageCells(p).map((cell, i) => (
                      <div key={STAGES[i]} className="flex min-h-8 items-center justify-center">
                        <StageCell cell={cell} stage={STAGES[i]} />
                      </div>
                    ))}
                    <Button type="button" variant="outline" size="sm" onClick={() => pick(p.id)} aria-label={`${p.company} ${p.title} 상태 바꾸기`}>
                      바꾸기
                    </Button>
                  </div>
                ))}
              </div>
            </Panel>
          ) : (
            <p className="text-sm text-text-secondary">아직 지원한 공고가 없습니다. 내 공고에서 결정을 "지원함"으로 바꾸면 여기에 들어옵니다.</p>
          )}
          <p className="text-label-sm text-text-secondary">지금 단계와 그 결과만 적습니다. 앞 단계는 통과한 것으로 보여 줍니다.</p>
        </section>

        <aside className={joinClasses(sideColumn(320), "gap-6")}>
          {selected && <StateEditor key={selected.id} posting={selected} groups={groups} />}
        </aside>
      </div>
    </Page>
  )
}

/** 단계 칸 하나: 통과(체크), 진행 중, 불합격(엑스), 아직(짧은 줄). */
function StageCell({ cell, stage }: { cell: ReturnType<typeof stageCells>[number]; stage: Stage }) {
  const pill = "inline-flex h-[26px] items-center gap-1 rounded-full text-xs font-bold whitespace-nowrap"
  if (cell === "pass")
    return (
      <span className={`${pill} bg-accent-subtle pr-2.5 pl-1.5 text-accent-strong`}>
        <CheckIcon aria-hidden="true" className="size-3.5" />
        <span className="sr-only">{STAGE_LABELS[stage]} </span>
        <span>합격</span>
      </span>
    )
  if (cell === "now")
    return (
      <span className={`${pill} bg-primary px-2.5 text-primary-foreground`}>
        <span className="sr-only">{STAGE_LABELS[stage]} </span>
        진행 중
      </span>
    )
  if (cell === "fail")
    return (
      <span className={`${pill} bg-surface-sunken pr-2.5 pl-1.5 text-foreground`}>
        <XIcon aria-hidden="true" className="size-3.5" />
        <span className="sr-only">{STAGE_LABELS[stage]} </span>
        <span>불합격</span>
      </span>
    )
  return <span role="img" aria-label={`${STAGE_LABELS[stage]} 아직`} className="h-0.5 w-4 rounded-[1px] bg-border" />
}

/** 상태 바꾸기: 결정, 지원함이면 단계와 결과. 저장해야 바뀐다. */
function StateEditor({ posting, groups }: { posting: MyPosting; groups: Group[] }) {
  const revalidator = useRevalidator()
  const [state, setState] = useState<MyState>({ decision: posting.decision, stage: posting.stage, result: posting.result })
  const [saving, setSaving] = useState(false)
  // 저장한 그 상태일 때만 "저장했습니다."를 알린다. 고치면 새 상태라 지워진다
  const [savedState, setSavedState] = useState<MyState | null>(null)
  const applied = state.decision === "applied"
  const where = posting.groups.map((g) => groups.find((x) => x.id === g.id)).filter((g): g is Group => g !== undefined)

  async function save() {
    setSaving(true)
    try {
      await updateMyState(posting.id, state)
      await revalidator.revalidate()
      setSavedState(state)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Panel aria-labelledby="board-edit" className="flex flex-col gap-5 p-6">
      <div className="flex flex-col gap-1">
        <h2 id="board-edit" className={sectionTitle}>
          상태 바꾸기
        </h2>
        <p className="text-sm text-text-secondary">
          {posting.company} · {posting.title}
        </p>
      </div>
      <div className="flex flex-col gap-2">
        <span className="text-sm font-medium">결정</span>
        <SegmentedControl
          label="결정"
          value={state.decision}
          onValueChange={(d) => setState((s) => withDecision(s, d as Decision))}
          options={DECISIONS.map((d) => ({ value: d, label: DECISION_LABELS[d] }))}
          className="self-start"
        />
      </div>
      {applied ? (
        <>
          <div className="flex flex-col gap-2">
            <span className="text-sm font-medium">단계</span>
            <SegmentedControl
              label="단계"
              value={state.stage ?? "document"}
              onValueChange={(stage) => setState((s) => ({ ...s, stage: stage as Stage }))}
              options={STAGES.map((s) => ({ value: s, label: STAGE_LABELS[s] }))}
              className="self-start"
            />
          </div>
          <div className="flex flex-col gap-2">
            <span className="text-sm font-medium">결과</span>
            <SegmentedControl
              label="결과"
              value={state.result ?? "in_progress"}
              onValueChange={(result) => setState((s) => ({ ...s, result: result as Result }))}
              options={RESULTS.map((r) => ({ value: r, label: RESULT_LABELS[r] }))}
              className="self-start"
            />
          </div>
        </>
      ) : (
        <p className="text-sm text-text-secondary">단계와 결과는 "지원함"일 때만 적습니다.</p>
      )}
      <Button type="button" className="self-end px-6" disabled={saving} onClick={() => void save()}>
        저장
      </Button>
      <p role="status" className="sr-only">
        {savedState === state ? "저장했습니다." : ""}
      </p>
      <PanelNote className="pt-4">
        {where.length === 0
          ? "이 공고가 있는 그룹이 없습니다. 상태는 나만 봅니다."
          : where.length === 1
            ? `이 공고는 ${where[0].name}에 있습니다. 그 그룹에는 내 공개 범위(${DISCLOSURE_LABELS[where[0].myDisclosure]})만큼만 보입니다.`
            : `이 공고는 ${where.map((g) => g.name).join(", ")}에 있습니다. 그룹마다 정한 내 공개 범위만큼만 보입니다.`}
      </PanelNote>
    </Panel>
  )
}
