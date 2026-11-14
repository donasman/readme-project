import { useEffect, useRef, useState, type DragEvent } from "react"
import { Link, useLoaderData, useRevalidator } from "react-router"

import { Button, buttonVariants } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { FieldError, noteId } from "@/shared/components/form-field"
import { sectionTitle, subpageTitle } from "@/shared/components/heading"
import { columns, mainColumn, sideColumn } from "@/shared/components/page-layout"
import { Panel } from "@/shared/components/panel"
import { CheckIcon, FileTextIcon, LockIcon, UploadIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { cn, joinClasses, matchesAccept } from "@/shared/lib/utils"

import { deleteSource, getExtractionJob, getSources, startExtraction, uploadSource } from "../api"
import { formatChars } from "../card-text"
import { CardsSubpage, ProgressBar, StatBox } from "../components/card-bits"
import type { CardSource, ExtractionJob } from "../types"

export function cardSourcesLoader() {
  return getSources()
}

// shortcut: 크기(20MB)는 안내 글뿐이고 막지 않는다, 서버가 받는 형식과 크기를 정하면 여기서도 막는다(화면 API 대응 3.5의 24번)
const ACCEPT = ".pdf,.docx,.hwpx,.pptx,.txt,.md"
const POLL_MS = 1000

type Step =
  | { kind: "upload" }
  | { kind: "ready"; source: CardSource; chunks: number }
  | { kind: "running"; source: CardSource; job: ExtractionJob }
  | { kind: "done"; job: ExtractionJob }

/** 자료로 카드 만들기(6차): 올리기 → 읽은 뒤 → 뽑는 중 → 끝남. 뽑는 동안 화면이 1초마다 서버에 묻는다. */
export function CardSourcesPage() {
  const sources = useLoaderData<typeof cardSourcesLoader>()
  const revalidator = useRevalidator()
  // shortcut: 뽑는 중인 작업은 이 화면의 상태에만 있어 나갔다 오면 찾지 못한다, 진행 중인 작업을 찾는 방법이 정해지면 바꾼다(화면 API 대응 3.5의 23번)
  const [step, setStep] = useState<Step>({ kind: "upload" })

  // 뽑는 중: 1초마다 작업 상태를 묻고, 끝나면 "끝남"으로 넘어간다.
  const runningJobId = step.kind === "running" ? step.job.id : null
  useEffect(() => {
    if (runningJobId === null) return
    const timer = setInterval(async () => {
      const job = await getExtractionJob(runningJobId)
      setStep((s) => (s.kind !== "running" ? s : job.status === "DONE" ? { kind: "done", job } : { ...s, job }))
      if (job.status === "DONE") void revalidator.revalidate()
    }, POLL_MS)
    return () => clearInterval(timer)
  }, [runningJobId, revalidator])

  async function read(input: { file: File } | { text: string }) {
    const { source, chunks } = await uploadSource(input)
    setStep({ kind: "ready", source, chunks })
    void revalidator.revalidate()
  }

  return (
    <CardsSubpage>
      <div className="flex flex-col gap-2">
        <h1 className={subpageTitle}>자료로 카드 만들기</h1>
        <p className="text-text-secondary">이력서나 포트폴리오를 올리면 경험을 카드로 뽑습니다. 뽑은 카드는 하나씩 확인한 뒤에 씁니다.</p>
      </div>

      <div className={columns}>
        <div className={joinClasses(mainColumn, "gap-6")}>
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
                <Button
                  type="button"
                  className="px-6"
                  onClick={async () => setStep({ kind: "running", source: step.source, job: await startExtraction(step.source) })}
                >
                  카드 뽑기 시작
                </Button>
              </div>
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
function UploadStep({ onRead }: { onRead: (input: { file: File } | { text: string }) => Promise<void> }) {
  const fileInput = useRef<HTMLInputElement>(null)
  const textArea = useRef<HTMLTextAreaElement>(null)
  const [text, setText] = useState("")
  const [empty, setEmpty] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [wrongType, setWrongType] = useState(false)

  const take = (file: File | undefined) => {
    if (!file) return
    const ok = matchesAccept(file.name, ACCEPT)
    setWrongType(!ok)
    if (ok) void onRead({ file })
  }

  const drop = (event: DragEvent) => {
    event.preventDefault()
    setDragging(false)
    take(event.dataTransfer.files[0])
  }

  return (
    <Panel aria-labelledby="src-upload" className="flex flex-col gap-5 p-6">
      <h2 id="src-upload" className={sectionTitle}>
        자료 올리기
      </h2>
      <div
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={drop}
        className={cn(
          "flex flex-col items-center gap-3 rounded-control border-[1.5px] border-dashed px-6 py-8 text-center",
          dragging ? "border-primary bg-accent-subtle" : "border-border-strong bg-surface-muted"
        )}
      >
        <UploadIcon aria-hidden="true" className="size-7 text-text-secondary" />
        <p className="font-bold">파일을 여기에 끌어 놓으세요</p>
        <p className="text-label-sm text-text-secondary">PDF, DOCX, HWPX, PPTX, TXT, MD · 20MB까지</p>
        <input
          ref={fileInput}
          type="file"
          accept={ACCEPT}
          className="sr-only"
          tabIndex={-1}
          aria-hidden="true"
          onChange={(e) => take(e.target.files?.[0])}
        />
        <Button type="button" aria-describedby={wrongType ? noteId("src-file") : undefined} onClick={() => fileInput.current?.click()}>
          파일 고르기
        </Button>
        {wrongType && (
          <div role="alert">
            <FieldError id="src-file">PDF, DOCX, HWPX, PPTX, TXT, MD 파일만 올릴 수 있습니다.</FieldError>
          </div>
        )}
      </div>
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
          aria-invalid={empty || undefined}
          aria-describedby={empty ? "src-text-error" : undefined}
          onChange={(e) => {
            setText(e.target.value)
            setEmpty(false)
          }}
          className="min-h-32"
        />
        {empty && (
          <p id="src-text-error" className="text-label-sm font-medium text-danger">
            읽을 글을 붙여넣어 주세요.
          </p>
        )}
        <Button
          type="button"
          variant="outline"
          className="self-end"
          onClick={() => {
            if (text.trim()) return void onRead({ text })
            setEmpty(true)
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

