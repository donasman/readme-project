import { useEffect, useRef, useState, type DragEvent, type FormEvent, type ReactNode } from "react"

import SegmentedControl from "@/components/arc/segmented-control/segmented-control"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { FieldError, FieldHint, FormField, noteId } from "@/shared/components/form-field"
import { Notice } from "@/shared/components/notice"
import { Panel } from "@/shared/components/panel"
import { UploadIcon } from "@/shared/icons"
import { FILE_TOO_LARGE, MAX_FILE_BYTES } from "@/shared/lib/api"
import { cn, isHttpUrl, matchesAccept } from "@/shared/lib/utils"

import { readPosting } from "./api"
import type { DuplicatePost, PostDraft, PostReadInput, ReadPostResult } from "./types"

type Via = "url" | "text" | "file"
const VIAS: { value: Via; label: string }[] = [
  { value: "url", label: "링크" },
  { value: "text", label: "본문 붙여넣기" },
  { value: "file", label: "파일" },
]
// 서버가 받는 형식과 크기(공고 문서 4.6). 큰 파일을 다 보낸 뒤 서버가 거절하지 않게 먼저 막는다
const ACCEPT = ".pdf,.docx,.hwpx,.hwp"
const TEXT_MAX = 50_000

type ReadNotice = Exclude<ReadPostResult, { status: "ok" }>

interface PostReadStepProps {
  /** 그룹에 올릴 때 그 그룹. 내 공고 넣기는 null이다(같은 공고를 보지 않는다) */
  groupId: string | null
  /** 읽으면 칸 확인으로 간다 */
  onDraft: (draft: PostDraft) => void
  /** 그 그룹에 이미 있는 공고의 알림(그룹에 올릴 때만 온다) */
  duplicateNotice?: (post: DuplicatePost) => ReactNode
}

/**
 * 공고 읽어 오기(시안 7차 "공고 올리기", 10차 "공고 넣기"): 링크, 본문 붙여넣기, 파일 가운데 하나로 읽는다.
 * 읽지 못하면 이유를 알리고 본문 붙여넣기로 넘어간다. 머리와 아래 안내는 부르는 화면이 둔다.
 */
export function PostReadStep({ groupId, onDraft, duplicateNotice }: PostReadStepProps) {
  const [via, setVia] = useState<Via>("url")
  const [notice, setNotice] = useState<(ReadNotice & { from: Via }) | null>(null)
  const [busy, setBusy] = useState(false)
  // 읽지 못한 링크. 본문 붙여넣기의 원문 링크와, 링크 탭으로 돌아왔을 때의 칸에 다시 넣는다
  const [failedUrl, setFailedUrl] = useState("")
  // 이 화면이 떠 있는가. 읽는 동안 다른 화면으로 가면 응답이 와도 칸 확인으로 넘기지 않는다
  const live = useRef(true)
  useEffect(() => {
    live.current = true
    return () => {
      live.current = false
    }
  }, [])

  async function read(input: PostReadInput) {
    setBusy(true)
    try {
      const result = await readPosting(groupId, input)
      if (result.status === "ok") return live.current ? onDraft(result.draft) : undefined
      setNotice({ ...result, from: input.kind })
      // 링크나 파일을 읽지 못하면 본문을 붙여넣게 한다
      if (result.status === "failed") {
        if (input.kind === "url") setFailedUrl(input.url)
        setVia("text")
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      {notice &&
        (notice.status === "duplicate" ? (
          duplicateNotice?.(notice.post)
        ) : (
          <Notice title={notice.from === "file" ? "이 파일은 읽지 못했습니다" : "이 링크는 읽지 못했습니다"}>
            <span>{notice.message}</span>
          </Notice>
        ))}

      <Panel aria-label="공고 넣기" className="flex flex-col gap-5 p-6">
        <SegmentedControl label="넣는 방법" value={via} onValueChange={(value) => setVia(value as Via)} options={VIAS} />
        {via === "url" && <UrlForm busy={busy} onRead={read} initialUrl={failedUrl} />}
        {via === "text" && <TextForm busy={busy} onRead={read} initialUrl={failedUrl} />}
        {via === "file" && <FileDrop busy={busy} onRead={read} />}
      </Panel>
    </>
  )
}

interface ReadProps {
  busy: boolean
  onRead: (input: PostReadInput) => Promise<void>
}

/** 링크와 본문 붙여넣기: 읽지 못한 링크가 있으면 그 주소로 칸을 채운다 */
type UrlReadProps = ReadProps & { initialUrl: string }

function UrlForm({ busy, onRead, initialUrl }: UrlReadProps) {
  const [url, setUrl] = useState(initialUrl)
  const [error, setError] = useState<string>()

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!url.trim()) return setError("공고 주소를 넣어 주세요.")
    if (!isHttpUrl(url.trim())) return setError("https:// 로 시작하는 주소를 넣어 주세요.")
    void onRead({ kind: "url", url: url.trim() })
  }

  return (
    <form onSubmit={submit} noValidate className="contents">
      <FormField
        id="pn-url"
        label="공고 링크"
        note={error ? <FieldError id="pn-url">{error}</FieldError> : <FieldHint id="pn-url">사람인, 잡코리아, 원티드, 점핏, 회사 채용 페이지의 공고 주소를 넣으세요.</FieldHint>}
      >
        <Input
          id="pn-url"
          type="url"
          placeholder="https://"
          value={url}
          aria-invalid={error ? true : undefined}
          aria-describedby={noteId("pn-url")}
          onChange={(e) => {
            setUrl(e.target.value)
            setError(undefined)
          }}
          className="md:text-base aria-invalid:ring-0"
        />
      </FormField>
      <Button type="submit" className="self-end" disabled={busy}>
        읽어 오기
      </Button>
    </form>
  )
}

function TextForm({ busy, onRead, initialUrl }: UrlReadProps) {
  const [text, setText] = useState("")
  const [sourceUrl, setSourceUrl] = useState(initialUrl)
  const [error, setError] = useState<{ text?: string; url?: string }>({})

  function submit(event: FormEvent) {
    event.preventDefault()
    const next = {
      // 서버가 받는 길이(공고 문서 4.6). 넘으면 400이라 여기서 알린다
      text: !text.trim() ? "공고 본문을 붙여넣어 주세요." : text.length > TEXT_MAX ? "본문은 5만 자까지 넣을 수 있습니다." : undefined,
      url: !sourceUrl.trim() || isHttpUrl(sourceUrl.trim()) ? undefined : "https:// 로 시작하는 주소를 넣거나 비워 두세요.",
    }
    setError(next)
    if (!next.text && !next.url) void onRead({ kind: "text", text, sourceUrl: sourceUrl.trim() })
  }

  return (
    <form onSubmit={submit} noValidate className="contents">
      <FormField id="pn-text" label="공고 본문" note={error.text && <FieldError id="pn-text">{error.text}</FieldError>}>
        <Textarea
          id="pn-text"
          rows={8}
          placeholder="공고 내용을 그대로 붙여넣으세요"
          value={text}
          aria-invalid={error.text ? true : undefined}
          aria-describedby={error.text ? noteId("pn-text") : undefined}
          onChange={(e) => {
            setText(e.target.value)
            setError((er) => ({ ...er, text: undefined }))
          }}
          className="min-h-[200px]"
        />
      </FormField>
      <FormField
        id="pn-src"
        label={
          <>
            원문 링크 <span className="font-normal text-text-secondary">(없으면 비워 둡니다)</span>
          </>
        }
        note={error.url && <FieldError id="pn-src">{error.url}</FieldError>}
      >
        <Input
          id="pn-src"
          type="url"
          placeholder="https://"
          value={sourceUrl}
          aria-invalid={error.url ? true : undefined}
          aria-describedby={error.url ? noteId("pn-src") : undefined}
          onChange={(e) => {
            setSourceUrl(e.target.value)
            setError((er) => ({ ...er, url: undefined }))
          }}
          className="md:text-base aria-invalid:ring-0"
        />
      </FormField>
      <Button type="submit" className="self-end" disabled={busy}>
        칸 채우기
      </Button>
    </form>
  )
}

/** 파일: 끌어 놓거나 고르기. */
function FileDrop({ busy, onRead }: ReadProps) {
  const fileInput = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  // 같은 이유로 이어서 막혀도 매번 알리도록 횟수를 알림의 key로 쓴다
  const [fileError, setFileError] = useState<{ message: string; tries: number } | null>(null)
  const failFile = (message: string | null) => setFileError((e) => (message ? { message, tries: (e?.tries ?? 0) + 1 } : null))

  const take = (file: File | undefined) => {
    // 읽는 중에 끌어 놓은 파일은 받지 않는다(버튼만 막혀 있어 두 번 읽고 늦게 끝난 쪽이 칸 확인을 덮는다)
    if (!file || busy) return
    if (!matchesAccept(file.name, ACCEPT)) return failFile("PDF, DOCX, HWPX, HWP 파일만 올릴 수 있습니다.")
    if (file.size > MAX_FILE_BYTES) return failFile(FILE_TOO_LARGE)
    failFile(null)
    void onRead({ kind: "file", file })
  }

  const drop = (event: DragEvent) => {
    event.preventDefault()
    setDragging(false)
    take(event.dataTransfer.files[0])
  }

  return (
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
      <p className="font-bold">공고문 파일을 여기에 끌어 놓으세요</p>
      <p className="text-label-sm text-text-secondary">PDF, DOCX, HWPX, HWP · 20MB까지</p>
      <input
        ref={fileInput}
        type="file"
        accept={ACCEPT}
        className="sr-only"
        tabIndex={-1}
        aria-hidden="true"
        onChange={(e) => {
          take(e.target.files?.[0])
          // 같은 파일을 다시 골라도 change가 오도록 비운다
          e.target.value = ""
        }}
      />
      <Button type="button" disabled={busy} aria-describedby={fileError ? noteId("pn-file") : undefined} onClick={() => fileInput.current?.click()}>
        파일 고르기
      </Button>
      {fileError && (
        <div role="alert" key={fileError.tries}>
          <FieldError id="pn-file">{fileError.message}</FieldError>
        </div>
      )}
    </div>
  )
}
