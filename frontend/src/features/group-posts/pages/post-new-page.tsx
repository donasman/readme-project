import { useRef, useState, type DragEvent, type FormEvent } from "react"
import { Link, useNavigate } from "react-router"

import SegmentedControl from "@/components/arc/segmented-control/segmented-control"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { useGroup } from "@/domain/group/use-group"
import { BackLink } from "@/shared/components/back-link"
import { FieldError, FieldHint, FormField, noteId } from "@/shared/components/form-field"
import { subpageTitle } from "@/shared/components/heading"
import { Panel } from "@/shared/components/panel"
import { UploadIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { cn } from "@/shared/lib/utils"

import { readPost } from "../api"
import type { PostReadInput, ReadPostResult } from "../types"
import type { PostCheckState } from "./post-check-page"

type Via = "url" | "text" | "file"
const VIAS: { value: Via; label: string }[] = [
  { value: "url", label: "링크" },
  { value: "text", label: "본문 붙여넣기" },
  { value: "file", label: "파일" },
]
// shortcut: 크기(20MB)는 안내 글뿐이고 막지 않는다, 서버가 받는 형식과 크기를 정하면 여기서도 막는다(화면 API 대응 3.2의 7번)
const ACCEPT = ".pdf,.docx,.hwpx,.hwp"

type Notice = Exclude<ReadPostResult, { status: "ok" }>

const isHttpUrl = (value: string) => {
  try {
    return ["http:", "https:"].includes(new URL(value).protocol)
  } catch {
    return false
  }
}

/** 공고 올리기(시안 7차): 링크, 본문 붙여넣기, 파일 가운데 하나로 읽고 칸 확인으로 간다. */
export function PostNewPage() {
  const { group } = useGroup()
  const navigate = useNavigate()
  const [via, setVia] = useState<Via>("url")
  const [notice, setNotice] = useState<Notice | null>(null)
  const [busy, setBusy] = useState(false)

  async function read(input: PostReadInput) {
    setBusy(true)
    try {
      const result = await readPost(group.id, input)
      if (result.status === "ok") {
        const state: PostCheckState = { draft: result.draft }
        return navigate(paths.groupPostCheck(group.id), { state })
      }
      setNotice(result)
      // 링크를 읽지 못하면 본문을 붙여넣게 한다
      if (result.status === "failed") setVia("text")
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="mx-auto flex max-w-[760px] flex-col gap-6 px-4 pt-6 pb-16">
      <BackLink to={paths.groupPosts(group.id)}>공고</BackLink>
      <div className="flex flex-col gap-1">
        <p className="text-sm text-text-secondary">{group.name}</p>
        <h1 className={subpageTitle}>공고 올리기</h1>
      </div>

      {notice && (
        <div role="status" className="flex flex-col gap-2 rounded-control bg-highlight-subtle px-5 py-4 text-sm">
          {notice.status === "duplicate" ? (
            <>
              <strong className="font-bold">이미 이 그룹에 있는 공고입니다</strong>
              <span>
                {notice.post.company} · {notice.post.title}. {notice.post.postedBy.name} 님이 {notice.post.postedAt}에 올렸습니다.
              </span>
              <Link
                to={paths.groupPost(group.id, notice.post.id)}
                className="-my-2 inline-flex min-h-control-md items-center self-start font-bold whitespace-nowrap text-primary"
              >
                그 공고 보기
              </Link>
            </>
          ) : (
            <>
              <strong className="font-bold">이 링크는 읽지 못했습니다</strong>
              <span>로그인이 필요하거나 내용이 그림으로 된 페이지일 수 있습니다. 공고 본문을 붙여넣어 주세요.</span>
            </>
          )}
        </div>
      )}

      <Panel aria-label="공고 넣기" className="flex flex-col gap-5 p-6">
        <SegmentedControl label="넣는 방법" value={via} onValueChange={(value) => setVia(value as Via)} options={VIAS} />
        {via === "url" && <UrlForm busy={busy} onRead={read} />}
        {via === "text" && <TextForm busy={busy} onRead={read} />}
        {via === "file" && <FileDrop busy={busy} onRead={read} />}
      </Panel>

      <p className="text-label-sm text-text-secondary">올리면 그룹 멤버 모두에게 보입니다. 그룹에서 내리는 것은 올린 사람과 그룹장만 할 수 있습니다.</p>
    </main>
  )
}

interface ReadProps {
  busy: boolean
  onRead: (input: PostReadInput) => Promise<void>
}

function UrlForm({ busy, onRead }: ReadProps) {
  const [url, setUrl] = useState("")
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

function TextForm({ busy, onRead }: ReadProps) {
  const [text, setText] = useState("")
  const [sourceUrl, setSourceUrl] = useState("")
  const [error, setError] = useState<{ text?: string; url?: string }>({})

  function submit(event: FormEvent) {
    event.preventDefault()
    const next = {
      text: text.trim() ? undefined : "공고 본문을 붙여넣어 주세요.",
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

  const drop = (event: DragEvent) => {
    event.preventDefault()
    setDragging(false)
    const file = event.dataTransfer.files[0]
    if (file) void onRead({ kind: "file", file })
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
          const file = e.target.files?.[0]
          if (file) void onRead({ kind: "file", file })
        }}
      />
      <Button type="button" disabled={busy} onClick={() => fileInput.current?.click()}>
        파일 고르기
      </Button>
    </div>
  )
}
