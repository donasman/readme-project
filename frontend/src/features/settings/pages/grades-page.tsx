import { useEffect, useState } from "react"

import SegmentedControl from "@/components/arc/segmented-control/segmented-control"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ChoiceInput } from "@/shared/components/choice-input"
import { FileDrop } from "@/shared/components/file-drop"
import { unreadInput } from "@/shared/components/form-field"
import { Heading, sectionTitle } from "@/shared/components/heading"
import { Notice } from "@/shared/components/notice"
import { Panel } from "@/shared/components/panel"
import { Pill } from "@/shared/components/pill"
import { textLink } from "@/shared/components/text-link"
import { CheckIcon, ChevronRightIcon, CopyIcon, LockIcon } from "@/shared/icons"
import { copyText } from "@/shared/lib/clipboard"
import { docTitle, joinClasses } from "@/shared/lib/utils"

import { readTranscript } from "../api"
import { EXAMPLE_TRANSCRIPT_NAME, exampleTranscript } from "../grade-example"
import {
  allText,
  applyEdits,
  convertGpa,
  countedCourses,
  courseLine,
  formatGpa,
  gradeStats,
  groupCourses,
  groupText,
  isMajor,
  SCALES,
  semesterCount,
  toHundred,
  TRANSCRIPT_ACCEPT,
  transcriptFileProblem,
  uncertainCount,
  uncertainFields,
  type CopyFormat,
  type CourseEdit,
  type GroupBy,
} from "../grade-rules"
import type { GradeScale, Transcript } from "../types"

type Step = { kind: "upload" } | { kind: "reading"; fileName: string } | { kind: "result"; fileName: string; transcript: Transcript }

/**
 * 학점 계산기(최종 시안 "학점 계산기 · 올리기", "학점 계산기 · 결과"): 성적증명서를 서버가 읽고, 합계·환산·묶기·복사는 화면이 한다(학점 계산기 문서).
 * 서버도 화면도 결과를 저장하지 않는다. 화면을 나가면 사라진다.
 */
export function GradesPage() {
  const [step, setStep] = useState<Step>({ kind: "upload" })

  // 돌려준 글은 놓는 곳 아래에 보인다. 올리기 칸은 읽는 동안 감춰 두기만 해서 그 알림이 남는다
  const read = async (file: File): Promise<string | null> => {
    const problem = transcriptFileProblem(file)
    if (problem) return problem
    setStep({ kind: "reading", fileName: file.name })
    const result = await readTranscript(file).catch((error: unknown) => {
      setStep({ kind: "upload" })
      throw error
    })
    if (!result.ok) {
      setStep({ kind: "upload" })
      return result.message
    }
    setStep({ kind: "result", fileName: file.name, transcript: result.transcript })
    return null
  }

  return (
    <>
      <title>{docTitle("학점 계산기", "설정")}</title>
      <div className="flex flex-col gap-2">
        <Heading level={2} size="text-[22px]">
          학점 계산기
        </Heading>
        <p className="text-text-secondary">성적증명서를 올리면 과목, 학점, 성적을 읽어 지원서 칸에 바로 붙여 넣을 수 있게 정리합니다.</p>
        <p className="flex items-start gap-2 text-label-sm text-text-secondary">
          <LockIcon aria-hidden="true" className="mt-px size-4 flex-none" />
          <span>파일은 AI(Claude)가 읽습니다. 올린 파일과 결과는 저장하지 않습니다. 이 화면을 나가면 사라집니다.</span>
        </p>
      </div>
      <p role="status" className="sr-only">
        {step.kind === "reading" ? "성적증명서를 읽는 중입니다." : step.kind === "result" ? "성적증명서를 읽었습니다." : ""}
      </p>

      <section aria-labelledby="gc-up" hidden={step.kind !== "upload"} className="flex flex-col gap-4">
        <h3 id="gc-up" className="sr-only">
          성적증명서 올리기
        </h3>
        <FileDrop
          id="gc-file"
          title="성적증명서를 끌어 놓거나 골라 주세요"
          hint={
            <>
              PDF · 10MB까지 · 여러 쪽이면 한 파일로
              <br />
              사진(JPG, PNG, GIF, WebP) · 5MB까지
            </>
          }
          accept={TRANSCRIPT_ACCEPT}
          onFile={read}
        />
        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
          <p className="text-label-sm text-text-secondary">학교 포털에서 받은 PDF가 가장 잘 읽힙니다. 사진은 종이가 반듯하게 보이도록 찍어 주세요.</p>
          <button
            type="button"
            onClick={() => setStep({ kind: "result", fileName: EXAMPLE_TRANSCRIPT_NAME, transcript: exampleTranscript() })}
            className={joinClasses(textLink, "inline-flex min-h-11 flex-none cursor-pointer items-center text-sm no-underline")}
          >
            예시 성적증명서로 보기
          </button>
        </div>
      </section>

      {step.kind === "reading" && (
        <Panel aria-labelledby="gc-reading" className="flex flex-col gap-4 p-6">
          <h3 id="gc-reading" className={joinClasses(sectionTitle, "[overflow-wrap:anywhere]")}>
            {step.fileName} 읽는 중
          </h3>
          <div aria-hidden="true" className="h-2 overflow-hidden rounded-full bg-control-track">
            <div className="h-full w-2/5 rounded-full bg-primary motion-safe:animate-pulse" />
          </div>
          {/* 서버는 진행을 알려 주지 않는다(학점 계산기 문서 5절 3번) */}
          <p className="text-sm text-text-secondary">AI가 학기, 이수구분, 과목명, 학점, 성적을 찾고 있습니다. 파일에 따라 수십 초 걸릴 수 있습니다.</p>
        </Panel>
      )}

      {step.kind === "result" && <GradeResult fileName={step.fileName} transcript={step.transcript} onReset={() => setStep({ kind: "upload" })} />}
    </>
  )
}

const GROUP_OPTIONS: { value: GroupBy; label: string }[] = [
  { value: "term", label: "학기별" },
  { value: "year", label: "학년별" },
  { value: "category", label: "이수구분별" },
]
const FORMAT_OPTIONS: { value: CopyFormat; label: string }[] = [
  { value: "table", label: "표로 복사" },
  { value: "text", label: "글로 복사" },
]
const FORMAT_HINTS: Record<CopyFormat, string> = {
  table: "표로 복사: 칸 사이를 탭으로 나눠 엑셀, 시트, 표로 된 지원서 칸에 그대로 들어갑니다.",
  text: "글로 복사: 한 줄에 과목 하나씩, 이수과목을 한 칸에 적는 지원서용입니다.",
}
const PASS_FAIL = new Set(["P", "S", "NP", "U"])
// 과목 줄의 칸: 이수구분, 과목명, 학점, 성적, 줄 복사. 휴대폰에서는 이수구분을 감춘다
const rowGrid = "grid grid-cols-[minmax(0,1fr)_44px_52px_44px] gap-2 sm:grid-cols-[96px_minmax(0,1fr)_56px_56px_44px]"
const cellButton = "inline-flex min-h-11 min-w-0 cursor-copy items-center justify-self-start rounded-[8px] px-2 text-left hover:bg-surface-muted"

/** 결과: 합계, 만점·묶기·복사 형식 고르기, 묶음마다 과목 줄. 확인 필요 칸을 고치면 합계와 복사에 바로 들어간다. */
function GradeResult({ fileName, transcript, onReset }: { fileName: string; transcript: Transcript; onReset: () => void }) {
  // 성적증명서에 만점이 적혀 있으면 그 값으로 시작한다(GC4)
  const [scale, setScale] = useState<GradeScale>(transcript.scale === 4.3 ? 4.3 : 4.5)
  const [groupBy, setGroupBy] = useState<GroupBy>("term")
  const [format, setFormat] = useState<CopyFormat>("table")
  const [dropRetake, setDropRetake] = useState(true)
  const [edits, setEdits] = useState<Record<number, CourseEdit>>({})
  const [open, setOpen] = useState<Record<string, boolean>>({})
  const [toast, setToast] = useState<{ text: string; ok: boolean; count: number } | null>(null)

  useEffect(() => {
    if (!toast) return
    const timer = setTimeout(() => setToast(null), 2200)
    return () => clearTimeout(timer)
  }, [toast])

  const copy = async (text: string, done: string) => {
    const ok = await copyText(text)
    setToast((t) => ({ text: ok ? done : "복사하지 못했습니다. 브라우저가 클립보드를 막았습니다.", ok, count: (t?.count ?? 0) + 1 }))
  }
  const edit = (index: number, change: CourseEdit) => setEdits((e) => ({ ...e, [index]: { ...e[index], ...change } }))

  const courses = applyEdits(transcript.courses, edits).map((course, index) => ({ ...course, index }))
  const counted = countedCourses(courses, dropRetake)
  const total = gradeStats(counted, scale)
  const major = gradeStats(counted.filter(isMajor), scale)
  const other: GradeScale = scale === 4.5 ? 4.3 : 4.5
  const groups = groupCourses(courses, groupBy).map((g) => ({ ...g, counted: countedCourses(g.courses, dropRetake) }))
  const unsure = uncertainCount(transcript.courses, scale)
  const where = [transcript.school, transcript.department].filter(Boolean).join(" ")

  const summary = [
    { label: "총 취득학점", value: String(total.credits), unit: "학점" },
    { label: "평점평균", value: formatGpa(total.gpa), unit: `/ ${scale}` },
    { label: "전공 평점", value: formatGpa(major.gpa), unit: `/ ${scale}` },
    { label: `${other} 만점 환산`, value: convertGpa(total.gpa, scale, other), unit: `/ ${other}` },
    { label: "100점 환산", value: toHundred(total.gpa, scale), unit: "점" },
  ]

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 rounded-control border border-border bg-surface py-3 pr-3 pl-5">
        <span className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1 text-sm">
          <strong className="font-bold [overflow-wrap:anywhere]">{fileName}</strong>
          <span className="text-text-secondary">{[where, `${semesterCount(transcript.courses)}개 학기`, `과목 ${transcript.courses.length}개`].filter(Boolean).join(" · ")}</span>
        </span>
        <Button type="button" variant="outline" size="sm" onClick={onReset}>
          다른 파일 올리기
        </Button>
      </div>

      {unsure > 0 && (
        <Notice title={`확인 필요 ${unsure}칸`}>
          <span>확실히 읽지 못했거나 고른 만점으로 평점을 매길 수 없는 칸에 노란 바탕을 칠했습니다. 원본과 견주어 고쳐 주세요. 고친 값은 계산과 복사에 바로 들어갑니다.</span>
        </Notice>
      )}

      <section aria-labelledby="gc-sum" className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
          <h3 id="gc-sum" className={sectionTitle}>
            합계
          </h3>
          <SegmentedControl
            label="만점"
            value={String(scale)}
            onValueChange={(v) => setScale(Number(v) as GradeScale)}
            options={SCALES.map((s) => ({ value: String(s), label: `${s} 만점` }))}
          />
        </div>
        <dl className="grid grid-cols-[repeat(auto-fit,minmax(150px,1fr))] gap-3">
          {summary.map((s) => (
            <div key={s.label} className="flex flex-col gap-1.5 rounded-control border border-border bg-surface py-3 pr-3 pl-5">
              <dt className="text-label-sm text-text-secondary">{s.label}</dt>
              <dd className="flex items-center justify-between gap-2">
                <span className="text-[28px] leading-[1.1] font-bold">
                  {s.value}
                  <span className="ml-1 text-sm font-semibold text-text-secondary">{s.unit}</span>
                </span>
                <Button type="button" variant="secondary" size="icon-sm" aria-label={`${s.label} 복사`} onClick={() => void copy(s.value, `복사함: ${s.label} ${s.value}`)}>
                  <CopyIcon aria-hidden="true" />
                </Button>
              </dd>
            </div>
          ))}
        </dl>
        <p className="text-label-sm text-text-secondary">
          {other} 만점과 100점 환산은 단순 비례(평점 ÷ {scale} × 만점)입니다. 회사마다 환산표가 다르니 지원서 안내를 따르세요. P/F 과목은 취득학점에만 들어가고 평점에는 들어가지
          않습니다.
        </p>
      </section>

      <div className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
          <SegmentedControl label="묶는 방식" value={groupBy} onValueChange={(v) => setGroupBy(v as GroupBy)} options={GROUP_OPTIONS} />
          <SegmentedControl label="복사 형식" value={format} onValueChange={(v) => setFormat(v as CopyFormat)} options={FORMAT_OPTIONS} />
          <label className="inline-flex min-h-11 cursor-pointer items-center gap-2 text-sm font-medium">
            <ChoiceInput type="checkbox" checked={dropRetake} onChange={(e) => setDropRetake(e.target.checked)} />
            <span>재수강 전 성적 빼기</span>
          </label>
          <Button
            type="button"
            className="ml-auto"
            onClick={() => void copy(allText(groups.map((g) => ({ title: g.title, courses: g.counted })), format), `전체 ${counted.length}과목을 복사했습니다`)}
          >
            <CopyIcon data-icon="inline-start" aria-hidden="true" />
            전체 복사
          </Button>
        </div>
        <p className="text-label-sm text-text-secondary">{FORMAT_HINTS[format]} 칸 하나를 누르면 그 값만 복사됩니다.</p>
      </div>

      {groups.map((g, gi) => {
        const openKey = `${groupBy}:${g.key}`
        const isOpen = open[openKey] ?? gi < 2
        const stats = gradeStats(g.counted, scale)
        return (
          <Panel key={openKey} aria-label={g.title} className="flex flex-col">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-2 py-3 pr-3 pl-5">
              <button
                type="button"
                aria-expanded={isOpen}
                onClick={() => setOpen((o) => ({ ...o, [openKey]: !isOpen }))}
                className="inline-flex min-h-11 min-w-0 flex-auto cursor-pointer items-center gap-2.5 text-left"
              >
                <ChevronRightIcon aria-hidden="true" className={joinClasses("size-4 flex-none transition-transform", isOpen && "rotate-90")} />
                <span className="font-bold">{g.title}</span>
                <span className="text-sm text-text-secondary">
                  {stats.credits}학점 · 평점 {formatGpa(stats.gpa)} · {g.counted.length}과목
                </span>
              </button>
              <Button type="button" variant="outline" size="sm" onClick={() => void copy(groupText(g.counted, format), `${g.title} ${g.counted.length}과목을 복사했습니다`)}>
                <CopyIcon data-icon="inline-start" aria-hidden="true" />
                {g.title} 복사
              </Button>
            </div>
            {isOpen && (
              <div className="px-5 pb-3">
                <div aria-hidden="true" className={joinClasses(rowGrid, "border-t border-border-subtle py-2 text-xs font-bold text-text-secondary")}>
                  <span className="max-sm:hidden">이수구분</span>
                  <span>과목명</span>
                  <span>학점</span>
                  <span>성적</span>
                  <span />
                </div>
                {g.courses.map((c) => {
                  const original = transcript.courses[c.index]
                  const check = uncertainFields(original, scale)
                  const out = dropRetake && c.retake === "before"
                  const tag =
                    c.retake === "before" ? (dropRetake ? "재수강 전 · 계산에서 뺌" : "재수강 전") : c.retake === "after" ? "재수강" : PASS_FAIL.has(c.grade) ? "P/F" : ""
                  const when = groupBy === "term" ? "" : `${c.year}-${c.term}`
                  const label = [when, tag].filter(Boolean).join(" ")
                  return (
                    <div key={c.index} className={joinClasses(rowGrid, "min-h-12 items-center border-t border-border-subtle", out && "opacity-50")}>
                      <button
                        type="button"
                        aria-label={`${c.name} 이수구분 ${c.category} 복사`}
                        onClick={() => void copy(c.category, `복사함: ${c.category}`)}
                        className={joinClasses(cellButton, "text-label-sm text-text-secondary max-sm:hidden")}
                      >
                        {c.category}
                      </button>
                      <span className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
                        <button
                          type="button"
                          aria-label={`과목명 ${c.name} 복사`}
                          onClick={() => void copy(c.name, `복사함: ${c.name}`)}
                          className={joinClasses(cellButton, "-ml-2 font-medium", out && "line-through")}
                        >
                          {c.name}
                        </button>
                        {label && <Pill tone="muted">{label}</Pill>}
                      </span>
                      {check.credit ? (
                        <Input
                          aria-label={`${c.name} 학점 (확인 필요)`}
                          inputMode="decimal"
                          value={edits[c.index]?.credit ?? String(original.credit)}
                          onChange={(e) => edit(c.index, { credit: e.target.value })}
                          className={joinClasses("h-9 px-2 md:text-base", unreadInput)}
                        />
                      ) : (
                        <button type="button" aria-label={`${c.name} 학점 ${c.credit} 복사`} onClick={() => void copy(String(c.credit), `복사함: ${c.credit}`)} className={cellButton}>
                          {c.credit}
                        </button>
                      )}
                      {check.grade ? (
                        <Input
                          aria-label={`${c.name} 성적 (확인 필요)`}
                          value={edits[c.index]?.grade ?? original.grade}
                          onChange={(e) => edit(c.index, { grade: e.target.value })}
                          className={joinClasses("h-9 px-2 font-bold md:text-base", unreadInput)}
                        />
                      ) : (
                        <button type="button" aria-label={`${c.name} 성적 ${c.grade} 복사`} onClick={() => void copy(c.grade, `복사함: ${c.grade}`)} className={joinClasses(cellButton, "font-bold")}>
                          {c.grade}
                        </button>
                      )}
                      <Button type="button" variant="ghost" size="icon" aria-label={`${c.name} 줄 복사`} onClick={() => void copy(courseLine(c, format), `${c.name} 줄을 복사했습니다`)} className="text-text-secondary">
                        <CopyIcon aria-hidden="true" />
                      </Button>
                    </div>
                  )
                })}
              </div>
            )}
          </Panel>
        )
      })}

      {/* 복사 결과 알림. 화면 읽기가 읽도록 상자는 늘 두고 글만 바꾼다 */}
      <div role="status" className="pointer-events-none fixed inset-x-4 bottom-6 z-50 flex justify-center">
        {toast && (
          <p key={toast.count} className="flex items-center gap-2 rounded-full bg-foreground px-4 py-3 text-sm font-semibold text-background shadow-floating">
            {toast.ok && <CheckIcon aria-hidden="true" className="size-4 flex-none" />}
            {toast.text}
          </p>
        )}
      </div>
    </>
  )
}
