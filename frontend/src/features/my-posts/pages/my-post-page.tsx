import { useRef, useState } from "react"
import { Link, useLoaderData, useNavigate, type LoaderFunctionArgs } from "react-router"

import { Button, buttonVariants } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { DISCLOSURE_LABELS } from "@/domain/group/disclosure"
import { statusDetail } from "@/domain/posting/my-state"
import { Avatar } from "@/shared/components/avatar"
import { BackLink } from "@/shared/components/back-link"
import { Chip } from "@/shared/components/chip"
import { Dot } from "@/shared/components/dot"
import { Heading, sectionTitle, subLabel } from "@/shared/components/heading"
import { Notice } from "@/shared/components/notice"
import { Page } from "@/shared/components/page"
import { columns, mainColumn, sideColumn } from "@/shared/components/page-layout"
import { Panel } from "@/shared/components/panel"
import { ExternalLinkIcon } from "@/shared/icons"
import { formatDateWeekday } from "@/shared/lib/date"
import { paths } from "@/shared/lib/paths"
import { useArrivalState } from "@/shared/lib/router"
import { docTitle, joinClasses } from "@/shared/lib/utils"

import { getMyPosting, getSchedules, removeMyPosting, saveMemo } from "../api"
import { DdayBadge } from "../components/my-bits"
import { RemoveDialog } from "../components/remove-dialog"
import { QuestionsPanel, RequirementsPanel } from "../components/requirement-panels"
import { removalOf, type Removal } from "../removal"
import { dueText } from "../status"
import type { ArrivedState, RemovedState } from "../types"

export async function myPostLoader({ params }: LoaderFunctionArgs) {
  // 전형 일정 수는 빼기 창이 알린다(상세 응답에 없다)
  const [detail, schedules] = await Promise.all([getMyPosting(params.postingId ?? ""), getSchedules()])
  return { ...detail, scheduleCount: schedules.filter((s) => s.postingId === detail.posting.id).length }
}

const sourceLink = "-my-3 inline-flex min-h-control-md items-center gap-1 font-bold whitespace-nowrap text-primary"

/** 공고 상세(시안 8차, 내 공고. 요건과 내 카드, 자기소개서 문항, 빼기는 12차): 마감과 지원처, 내 상태, 공고 내용(원문), 메모, 옆 칸에 요건과 내 카드, 이 공고가 있는 그룹. */
export function MyPostPage() {
  const { today, posting, scheduleCount } = useLoaderData<typeof myPostLoader>()
  const navigate = useNavigate()
  // 내 공고 넣기에서 이미 있는 공고로 왔을 때
  const arrived = useArrivalState<ArrivedState>()?.arrived ?? false
  const due = dueText(posting, today)
  const name = `${posting.company} · ${posting.title}`
  // 빼기 창: 열 때 저장한 메모로 지워질 것을 센다(창이 닫히는 동안에도 글이 남게 값은 두고 open만 끈다)
  const [removing, setRemoving] = useState(false)
  const [removal, setRemoval] = useState<Removal | null>(null)
  const [memo, setMemo] = useState(posting.memo)
  // 저장한 값과 비교한다. loader의 posting.memo는 저장해도 다시 읽지 않아 처음 값에 머문다
  const lastSaved = useRef(posting.memo)
  const [saved, setSaved] = useState(false)
  const full = posting.deadline.kind === "date" ? `${formatDateWeekday(posting.deadline.date)} 마감` : due.sub

  return (
    <Page narrow className="gap-6 pt-10">
      <title>{docTitle(posting.title, "내 공고")}</title>
      <BackLink to={paths.myPosts()}>내 공고</BackLink>
      {arrived && (
        <Notice title="이미 내 공고에 있는 공고입니다">
          <span>넣으려던 공고가 이미 있어 이 공고로 왔습니다. 지원 상태와 메모는 그대로입니다.</span>
        </Notice>
      )}
      <div className="flex flex-col gap-3">
        <p className="text-sm text-text-secondary">{posting.company}</p>
        <Heading level={1} size="text-3xl">
          {posting.title}
        </Heading>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
          <DdayBadge text={due.main} soon={due.soon} />
          <span>{full}</span>
          {posting.applyAt && <span className="text-text-secondary">지원하는 곳: {posting.applyAt}</span>}
          {/* 원문 주소가 없는 공고(링크 없이 본문을 붙여넣은 공고)는 갈 곳이 없어 감춘다 */}
          {posting.sourceUrl && (
            <a href={posting.sourceUrl} target="_blank" rel="noreferrer" className={sourceLink}>
              <span>원문 보기</span>
              <ExternalLinkIcon aria-hidden="true" className="size-4" />
            </a>
          )}
        </div>
      </div>

      <div className={columns}>
        <div className={joinClasses(mainColumn, "gap-6")}>
          <Panel aria-labelledby="mp-state" className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3 p-6">
            <div className="flex flex-col gap-1">
              <h2 id="mp-state" className={subLabel}>
                내 상태
              </h2>
              <p className="text-lg font-bold">{statusDetail(posting)}</p>
            </div>
            <Link to={paths.board(posting.id)} className={buttonVariants({ variant: "outline" })}>
              상태 바꾸기
            </Link>
          </Panel>

          <Panel aria-labelledby="mp-body" className="flex flex-col gap-5 p-6">
            <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
              <h2 id="mp-body" className={sectionTitle}>
                공고 내용
              </h2>
            </div>
            {!posting.analysis && (posting.sections.length > 0 || posting.keywords.length > 0) && (
              <p className="rounded-control bg-surface-muted px-4 py-3 text-sm">아직 요약이 없습니다. 공고를 넣은 직후에는 잠시 뒤에 생깁니다. 아래는 원문에서 읽은 내용입니다.</p>
            )}
            {posting.sections.length === 0 && posting.keywords.length === 0 && <p className="text-sm text-text-secondary">아직 정리된 공고 내용이 없습니다.</p>}
            {posting.sections.map((s) => (
              <div key={s.title} className="flex flex-col gap-2">
                <h3 className={subLabel}>{s.title}</h3>
                <ul className="flex flex-col gap-1.5">
                  {s.items.map((item) => (
                    <li key={item} className="flex items-start gap-2.5">
                      <Dot tone="muted" className="mt-[9px]" />
                      <span>{item}</span>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
            {posting.keywords.length > 0 && (
              <div className="flex flex-col gap-2">
                <h3 className={subLabel}>키워드</h3>
                <ul className="flex flex-wrap gap-1.5">
                  {posting.keywords.map((k) => (
                    <Chip key={k}>{k}</Chip>
                  ))}
                </ul>
              </div>
            )}
          </Panel>

          <Panel aria-labelledby="mp-memo" className="flex flex-col gap-4 p-6">
            <div className="flex flex-col gap-1">
              <h2 id="mp-memo" className={sectionTitle}>
                나만 보는 메모
              </h2>
              <p className="text-sm text-text-secondary">그룹에는 보이지 않습니다.</p>
            </div>
            <label htmlFor="mp-memo-text" className="sr-only">
              메모
            </label>
            {/* 칸을 떠나면 저장한다(시안에 저장 버튼이 없다) */}
            <Textarea
              id="mp-memo-text"
              rows={3}
              // 서버가 받는 길이(공고 문서 8절 16번). 넘으면 저장이 실패한다
              maxLength={2000}
              value={memo}
              onChange={(e) => {
                setMemo(e.target.value)
                setSaved(false)
              }}
              onBlur={async () => {
                if (memo === lastSaved.current) return
                await saveMemo(posting.id, memo)
                lastSaved.current = memo
                setSaved(true)
              }}
              className="min-h-24"
            />
            <p role="status" className={joinClasses("text-label-sm text-text-secondary", !saved && "sr-only")}>
              {saved ? "메모를 저장했습니다." : ""}
            </p>
          </Panel>
        </div>

        <aside className={joinClasses(sideColumn(320), "gap-6")}>
          {posting.analysis ? (
            <>
              <RequirementsPanel requirements={posting.requirements} matches={posting.matches} />
              {posting.analysis.questions.length > 0 && <QuestionsPanel questions={posting.analysis.questions} />}
            </>
          ) : (
            <Panel aria-labelledby="mp-match" className="flex flex-col gap-4 p-6">
              <h2 id="mp-match" className={sectionTitle}>
                맞는 카드
              </h2>
              <p className="text-sm text-text-secondary">아직 공고 분석이 없어 맞는 카드를 셀 수 없습니다.</p>
            </Panel>
          )}

          <Panel aria-labelledby="mp-groups" className="flex flex-col gap-4 p-6">
            <h2 id="mp-groups" className={sectionTitle}>
              이 공고가 있는 그룹
            </h2>
            {posting.groupStates.length > 0 ? (
              posting.groupStates.map((g) => (
                <Link key={g.id} to={paths.groupPost(g.id, g.postId)} className="flex min-h-13 items-center gap-3 text-foreground">
                  <Avatar size={32} tone="accent">
                    {g.mark}
                  </Avatar>
                  <span className="flex min-w-0 flex-col">
                    <span className="text-sm font-bold">{g.name}</span>
                    <span className="text-label-sm text-text-secondary">
                      {g.interested ? "관심 켬" : "관심 끔"} · 댓글 {g.commentCount} · 공개 범위: {DISCLOSURE_LABELS[g.disclosure]}
                    </span>
                  </span>
                </Link>
              ))
            ) : (
              <p className="text-sm text-text-secondary">이 공고가 있는 내 그룹이 없습니다.</p>
            )}
          </Panel>

          <Panel aria-labelledby="mp-remove" className="flex flex-col gap-3 p-6">
            <h2 id="mp-remove" className={sectionTitle}>
              내 공고에서 빼기
            </h2>
            <p className="text-sm text-text-secondary">지원 상태, 메모, 전형 일정이 지워지고 그룹에서 켠 관심이 꺼집니다. 공고와 댓글은 그룹에 남습니다.</p>
            <Button
              type="button"
              variant="danger"
              className="self-start"
              onClick={() => {
                setRemoval(removalOf(posting, lastSaved.current, scheduleCount))
                setRemoving(true)
              }}
            >
              내 공고에서 빼기
            </Button>
          </Panel>
        </aside>
        {removal && (
          <RemoveDialog
            open={removing}
          onClose={() => setRemoving(false)}
          name={name}
          removal={removal}
          onConfirm={async () => {
            await removeMyPosting(posting.id)
            const removed: RemovedState = { removed: { name, groups: posting.groupStates.filter((g) => g.interested).map((g) => g.name) } }
            void navigate(paths.myPosts(), { state: removed })
          }}
        />
        )}
      </div>
    </Page>
  )
}
