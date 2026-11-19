import { useEffect, useRef, useState } from "react"
import { Link, useLoaderData, useRevalidator } from "react-router"

import { Button, buttonVariants } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { getProfile } from "@/domain/profile/api"
import type { Profile } from "@/domain/profile/types"
import { FileDrop } from "@/shared/components/file-drop"
import { IconNote } from "@/shared/components/notice"
import { PanelHead, sectionTitle, subpageTitle } from "@/shared/components/heading"
import { columns, mainColumn, sideColumn } from "@/shared/components/page-layout"
import { Panel } from "@/shared/components/panel"
import { Pill } from "@/shared/components/pill"
import { CheckIcon, FileTextIcon, LockIcon } from "@/shared/icons"
import { ApiError, FILE_TOO_LARGE, MAX_FILE_BYTES } from "@/shared/lib/api"
import { objectParticle } from "@/shared/lib/korean"
import { paths } from "@/shared/lib/paths"
import { cn, docTitle, joinClasses, matchesAccept } from "@/shared/lib/utils"

import { deleteSource, getExtractionJob, getSources, startExtraction, uploadSource } from "../api"
import { formatChars } from "../card-text"
import { CardsSubpage, ProgressBar, StatBox } from "../components/card-bits"
import { profileCheckRows } from "../profile-check"
import type { CardSource, ExtractionJob } from "../types"

export function cardSourcesLoader() {
  return getSources()
}

// 서버가 받는 형식과 크기(경험카드 문서 4.4). 형식은 서버가 내용으로 다시 본다
const ACCEPT = ".pdf,.docx,.hwpx,.hwp,.pptx,.txt,.md"
const ACCEPT_TEXT = "PDF, DOCX, HWPX, HWP, PPTX, TXT, MD"
const POLL_MS = 1000

// before: 뽑기를 시작할 때의 프로필. 끝난 뒤의 프로필과 견주어 이번에 채운 칸을 표시한다. 나갔다 와서 이어 보면 null이다
type Step =
  | { kind: "upload" }
  | { kind: "ready"; source: CardSource; chunks: number }
  | { kind: "running"; source: CardSource; job: ExtractionJob; before: Profile | null }
  | { kind: "done"; job: ExtractionJob; before: Profile | null; profile: Profile }
  | { kind: "failed"; source: CardSource; job: ExtractionJob; before: Profile | null }

/**
 * 자료로 카드 만들기(6차): 올리기 → 읽은 뒤 → 뽑는 중 → 끝남(또는 실패). 뽑는 동안 화면이 1초마다 서버에 묻는다.
 * 실패하면 "이어서 뽑기"가 마친 조각 다음부터 다시 돌린다(경험카드 문서 4.6). 끝나면 뽑기가 채운 프로필 칸을 보인다(12차 시안).
 */
export function CardSourcesPage() {
  const sources = useLoaderData<typeof cardSourcesLoader>()
  const revalidator = useRevalidator()
  const [step, setStep] = useState<Step>({ kind: "upload" })
  const [startError, setStartError] = useState<string | null>(null)

  // 화면에 들어왔을 때 뽑는 중인 자료가 있으면(나갔다 온 경우) 그 작업을 이어서 보인다(화면 API 대응 3.5의 23번)
  const resumable = sources.find((s) => s.runningJobId !== null)
  useEffect(() => {
    if (!resumable?.runningJobId) return
    let stale = false
    void getExtractionJob(resumable.runningJobId).then((job) => {
      if (!stale && job?.status === "RUNNING") setStep((s) => (s.kind === "upload" ? { kind: "running", source: resumable, job, before: null } : s))
    })
    return () => {
      stale = true
    }
  }, [resumable])

  // 뽑는 중: 1초마다 작업 상태를 묻고, 끝나면 "끝남"이나 "실패"로 넘어간다. 자료를 지워 작업이 없어지면 올리기로 돌아간다.
  const runningJobId = step.kind === "running" ? step.job.id : null
  useEffect(() => {
    if (runningJobId === null) return
    const timer = setInterval(async () => {
      let job: ExtractionJob | null
      let profile: Profile | null
      try {
        job = await getExtractionJob(runningJobId)
        profile = job?.status === "DONE" ? await getProfile() : null
      } catch (error) {
        // 연결이 잠깐 끊기면 이번만 건너뛰고 다음에 다시 묻는다. 다른 오류는 묻기를 멈추고 한 번만 알린다
        if (error instanceof ApiError && error.status === 0) return
        clearInterval(timer)
        throw error
      }
      setStep((s) => {
        if (s.kind !== "running") return s
        if (job === null) return { kind: "upload" }
        if (job.status === "DONE" && profile) return { kind: "done", job, before: s.before, profile }
        if (job.status === "FAILED") return { kind: "failed", source: s.source, job, before: s.before }
        return { ...s, job }
      })
      if (job === null || job.status !== "RUNNING") void revalidator.revalidate()
    }, POLL_MS)
    return () => clearInterval(timer)
  }, [runningJobId, revalidator])

  /** 자료를 올린다. 읽지 못하면 그 이유를 돌려주고, 올리기 화면이 칸 아래에 알린다. */
  async function read(input: { file: File } | { text: string }): Promise<string | null> {
    const result = await uploadSource(input)
    if (!result.ok) return result.message
    setStartError(null)
    setStep({ kind: "ready", source: result.source, chunks: result.chunks })
    void revalidator.revalidate()
    return null
  }

  /** 뽑기를 시작한다. 이어서 뽑을 때는 처음 시작할 때의 프로필(before)을 넘겨, 앞서 채운 칸도 이번에 채운 칸으로 본다. */
  async function extract(source: CardSource, before?: Profile | null) {
    setStartError(null)
    const snapshot = before === undefined ? await getProfile() : before
    const result = await startExtraction(source)
    if (result.ok) setStep({ kind: "running", source, job: result.job, before: snapshot })
    else setStartError(result.message)
  }

  return (
    <CardsSubpage>
      <title>{docTitle("자료로 카드 만들기", "경험카드")}</title>
      <div className="flex flex-col gap-2">
        <h1 className={subpageTitle}>자료로 카드 만들기</h1>
        <p className="text-text-secondary">이력서나 포트폴리오를 올리면 경험을 카드로 뽑고, 프로필의 빈 칸을 채웁니다. 둘 다 확인한 뒤에 씁니다.</p>
      </div>

      <div className={columns}>
        <div className={joinClasses(mainColumn, "gap-6")}>
          <p role="status" className="sr-only">
            {step.kind === "ready"
              ? `${step.source.name}${objectParticle(step.source.name)} 읽었습니다.`
              : step.kind === "running"
                ? "카드를 뽑는 중입니다."
                : step.kind === "done"
                  ? `카드 ${step.job.newCards}장을 만들었습니다.`
                  : step.kind === "failed"
                    ? "카드를 다 뽑지 못했습니다."
                    : ""}
          </p>
          {step.kind === "upload" && <UploadStep onRead={read} />}
          {step.kind === "ready" && (
            <Panel aria-labelledby="src-ready" className="flex flex-col gap-5 p-6">
              <h2 id="src-ready" className={sectionTitle}>
                자료를 읽었습니다
              </h2>
              <div className="flex items-center gap-3 rounded-control bg-surface-muted p-4">
                <FileTextIcon aria-hidden="true" className="size-6 flex-none" />
                <div className="flex min-w-0 flex-col gap-0.5">
                  <span className="font-bold [overflow-wrap:anywhere]">{step.source.name}</span>
                  <span className="text-label-sm text-text-secondary">{formatChars(step.source.chars)}</span>
                </div>
              </div>
              <dl className="grid grid-cols-2 gap-3">
                <StatBox label="나눠 읽는 조각" value={`${step.chunks}개`} />
                <StatBox label="AI 호출" value={`${step.chunks}회`} />
              </dl>
              <p className="text-sm text-text-secondary">5,000자씩 나눠 읽습니다. 이미 있는 카드와 같은 경험은 건너뜁니다.</p>
              <div className="flex flex-wrap justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => setStep({ kind: "upload" })}>
                  다른 자료 올리기
                </Button>
                <Button type="button" className="px-6" onClick={() => void extract(step.source)}>
                  카드 뽑기 시작
                </Button>
              </div>
              {startError && (
                <div role="alert">
                  <IconNote tone="warning">{startError}</IconNote>
                </div>
              )}
            </Panel>
          )}
          {step.kind === "running" && (
            <Panel aria-labelledby="src-running" className="flex flex-col gap-5 p-6">
              <h2 id="src-running" className={sectionTitle}>
                카드를 뽑는 중입니다
              </h2>
              <div className="flex flex-col gap-2.5">
                <div className="flex items-baseline justify-between gap-3 text-sm">
                  <span className="font-bold">
                    조각 {step.job.doneChunks} / {step.job.totalChunks}
                  </span>
                  <span className="text-text-secondary">지금까지 카드 {step.job.cardsSoFar}장</span>
                </div>
                <ProgressBar label="카드 뽑기 진행" value={step.job.doneChunks} max={step.job.totalChunks} />
              </div>
              <p className="text-sm text-text-secondary">이 화면을 닫아도 계속됩니다. 끝나면 경험카드 목록에 "확인 필요"로 들어옵니다.</p>
            </Panel>
          )}
          {step.kind === "failed" && (
            <Panel aria-labelledby="src-failed" className="flex flex-col gap-5 p-6">
              <h2 id="src-failed" className={sectionTitle}>
                카드를 다 뽑지 못했습니다
              </h2>
              <div className="flex flex-col gap-2">
                <IconNote tone="danger">{step.job.error ?? "카드를 뽑다가 문제가 생겼습니다."}</IconNote>
                <p className="text-sm text-text-secondary">
                  조각 {step.job.totalChunks}개 가운데 {step.job.doneChunks}개를 마쳤습니다.
                  {step.job.cardsSoFar > 0 && ` 그동안 만든 카드 ${step.job.cardsSoFar}장은 "확인 필요"로 목록에 있습니다.`} 이어서 뽑으면 마친 조각 다음부터 다시 합니다.
                </p>
              </div>
              <div className="flex flex-wrap justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => setStep({ kind: "upload" })}>
                  자료 더 올리기
                </Button>
                <Button type="button" className="px-6" onClick={() => void extract(step.source, step.before)}>
                  이어서 뽑기
                </Button>
              </div>
              {startError && (
                <div role="alert">
                  <IconNote tone="warning">{startError}</IconNote>
                </div>
              )}
            </Panel>
          )}
          {step.kind === "done" && (
            <Panel aria-labelledby="src-done" className="flex flex-col gap-5 p-6">
              <div className="flex items-center gap-3">
                <span aria-hidden="true" className="inline-flex size-10 flex-none items-center justify-center rounded-control bg-accent-subtle text-accent-strong">
                  <CheckIcon className="size-5" />
                </span>
                <h2 id="src-done" className={sectionTitle}>
                  카드 {step.job.newCards}장을 만들었습니다
                </h2>
              </div>
              <ul className="flex flex-col gap-3 text-sm">
                <li className="flex flex-col gap-0.5">
                  <span className="text-xs font-bold text-text-secondary">새 카드</span>
                  <span>{step.job.newCards}장. 모두 "확인 필요"로 목록에 들어갔습니다.</span>
                </li>
                {step.job.skippedSameAs.length > 0 && (
                  <li className="flex flex-col gap-0.5">
                    <span className="text-xs font-bold text-text-secondary">건너뜀</span>
                    <span>
                      {step.job.skippedSameAs.length}개. 이미 있는 카드 {step.job.skippedSameAs.map((t) => `"${t}"`).join(", ")}과 같은 경험입니다.
                    </span>
                  </li>
                )}
              </ul>
              <div className="flex flex-wrap justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => setStep({ kind: "upload" })}>
                  자료 더 올리기
                </Button>
                <Link to={paths.cardReview()} className={buttonVariants({ className: "px-6" })}>
                  카드 확인하기
                </Link>
              </div>
            </Panel>
          )}
          {step.kind === "done" && (
            <Panel aria-labelledby="src-profile" className="flex flex-col gap-4 p-6">
              <PanelHead id="src-profile" title="프로필 칸 확인">
                자료에서 읽은 값은 프로필의 비어 있던 칸에만 넣었습니다. 이미 있던 값은 바꾸지 않았습니다.{" "}
                {step.before ? "이번에 채운 칸에 표시했습니다." : "지금 프로필은 아래와 같습니다."}
                
              </PanelHead>
              <dl className="flex flex-col">
                {profileCheckRows(step.profile, step.before).map((row) => (
                  <div key={row.label} className="flex flex-wrap items-baseline gap-x-4 gap-y-1 border-t border-border-subtle py-3">
                    <dt className="w-[72px] flex-none text-[13px] font-bold text-text-secondary">{row.label}</dt>
                    <dd className="flex min-w-0 flex-[1_1_240px] flex-col gap-2 text-sm">
                      {row.lines.length === 0 ? (
                        <span className="inline-flex h-6 items-center self-start rounded-full border-2 border-highlight-strong bg-highlight-subtle px-2.5 text-xs font-bold whitespace-nowrap">
                          비어 있음
                        </span>
                      ) : (
                        row.lines.map((line, i) => (
                          <span key={i} className="flex flex-col gap-0.5">
                            <span>{line.value}</span>
                            {line.note && <span className="text-[13px] text-text-secondary">{line.note}</span>}
                          </span>
                        ))
                      )}
                      {row.extra && <span className="text-[13px] text-text-secondary">{row.extra}</span>}
                    </dd>
                    {row.filled && (
                      <Pill tone="accent">
                        <CheckIcon aria-hidden="true" className="mr-0.5 size-3" />
                        이번에 채움
                      </Pill>
                    )}
                  </div>
                ))}
              </dl>
              <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
                <p className="min-w-0 flex-[1_1_240px] text-[13px] text-text-secondary">노란 칸은 자료에서 읽지 못해 비어 있는 칸입니다. 프로필은 나만 봅니다.</p>
                <Link to={paths.profile(true)} className={buttonVariants({ variant: "outline" })}>
                  프로필 확인하기
                </Link>
              </div>
            </Panel>
          )}
        </div>

        <aside className={joinClasses(sideColumn(300), "gap-3")}>
          <h2 className={sectionTitle}>올린 자료</h2>
          {sources.length > 0 ? (
            <Panel as="ul" className="flex flex-col">
              {sources.map((s, i) => (
                <li key={s.id} className={cn("flex items-center justify-between gap-3 py-4 pr-3 pl-5", i > 0 && "border-t border-border-subtle")}>
                  <div className="flex min-w-0 flex-col gap-0.5">
                    <span className="text-sm font-bold [overflow-wrap:anywhere]">{s.name}</span>
                    <span className="text-xs text-text-secondary">
                      {[s.createdAt, formatChars(s.chars), s.cardCount === null ? null : `카드 ${s.cardCount}장`].filter(Boolean).join(" · ")}
                    </span>
                  </div>
                  <button
                    type="button"
                    aria-label={`${s.name} 지우기`}
                    onClick={async () => {
                      await deleteSource(s.id)
                      void revalidator.revalidate()
                    }}
                    className="inline-flex min-h-control-md flex-none cursor-pointer items-center rounded-full px-3 text-label-sm font-bold whitespace-nowrap text-text-secondary hover:bg-surface-muted hover:text-foreground"
                  >
                    지우기
                  </button>
                </li>
              ))}
            </Panel>
          ) : (
            <p className="text-sm text-text-secondary">아직 올린 자료가 없습니다.</p>
          )}
          <p className="text-label-sm text-text-secondary">자료를 지워도 만든 카드는 남습니다.</p>
        </aside>
      </div>
    </CardsSubpage>
  )
}

/** 올리기: 파일을 끌어 놓거나 고르기, 또는 글 붙여넣기. */
function UploadStep({ onRead }: { onRead: (input: { file: File } | { text: string }) => Promise<string | null> }) {
  const textArea = useRef<HTMLTextAreaElement>(null)
  const [text, setText] = useState("")
  const [textError, setTextError] = useState<string | null>(null)

  const take = async (file: File) => {
    if (!matchesAccept(file.name, ACCEPT)) return `${ACCEPT_TEXT} 파일만 올릴 수 있습니다.`
    // 큰 파일을 다 보낸 뒤 서버가 거절하지 않게 먼저 막는다(경험카드 문서 6절 7번)
    if (file.size > MAX_FILE_BYTES) return FILE_TOO_LARGE
    return onRead({ file })
  }

  return (
    <Panel aria-labelledby="src-upload" className="flex flex-col gap-5 p-6">
      <h2 id="src-upload" className={sectionTitle}>
        자료 올리기
      </h2>
      <FileDrop id="src-file" title="파일을 여기에 끌어 놓으세요" hint={`${ACCEPT_TEXT} · 20MB까지`} accept={ACCEPT} onFile={take} />
      <div className="flex items-center gap-3 text-xs text-text-secondary">
        <span aria-hidden="true" className="h-px flex-1 bg-border" />
        <span>또는</span>
        <span aria-hidden="true" className="h-px flex-1 bg-border" />
      </div>
      <div className="flex flex-col gap-1.5">
        <label htmlFor="src-text" className="text-sm font-medium">
          글 붙여넣기
        </label>
        <Textarea
          ref={textArea}
          id="src-text"
          rows={5}
          placeholder="이력서나 포트폴리오의 글을 붙여넣으세요"
          value={text}
          aria-invalid={textError ? true : undefined}
          aria-describedby={textError ? "src-text-error" : undefined}
          onChange={(e) => {
            setText(e.target.value)
            setTextError(null)
          }}
          className="min-h-32"
        />
        {textError && (
          <p id="src-text-error" className="text-label-sm font-medium text-danger">
            {textError}
          </p>
        )}
        <Button
          type="button"
          variant="outline"
          className="self-end"
          onClick={async () => {
            if (text.trim()) return setTextError(await onRead({ text }))
            setTextError("읽을 글을 붙여넣어 주세요.")
            textArea.current?.focus()
          }}
        >
          이 글 읽기
        </Button>
      </div>
      <p className="flex items-start gap-2 text-label-sm text-text-secondary">
        <LockIcon aria-hidden="true" className="mt-px size-4 flex-none" />
        <span>올린 파일은 저장하지 않습니다. 읽어 낸 글만 보관하고, 아래 목록에서 언제든 지울 수 있습니다.</span>
      </p>
    </Panel>
  )
}

