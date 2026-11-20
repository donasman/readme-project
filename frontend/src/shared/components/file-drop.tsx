import { useRef, useState, type DragEvent, type ReactNode } from "react"

import { Button } from "@/components/ui/button"
import { FieldError, noteId } from "@/shared/components/form-field"
import { UploadIcon } from "@/shared/icons"
import { cn } from "@/shared/lib/utils"

interface FileDropProps {
  /** 칸 아래 오류 글의 id 바탕(noteId) */
  id: string
  /** 굵은 안내: "파일을 여기에 끌어 놓으세요" */
  title: string
  /** 받는 형식과 크기(13px 회색) */
  hint: ReactNode
  /** 고르기 창의 accept. 끌어 놓은 파일은 onFile이 다시 본다 */
  accept: string
  /** 읽는 중이면 고르기 버튼을 막고 끌어 놓은 파일도 받지 않는다 */
  busy?: boolean
  /** 받은 파일 하나를 다룬다. 칸 아래에 알릴 글이 있으면 돌려주고, 없으면 null(앞의 알림을 지운다) */
  onFile: (file: File) => string | null | Promise<string | null>
}

/** 파일 놓는 곳: 점선 상자에 끌어 놓거나 "파일 고르기"로 한 파일을 받는다. 자료 올리기, 공고 읽어 오기, 학점 계산기가 쓴다. */
export function FileDrop({ id, title, hint, accept, busy = false, onFile }: FileDropProps) {
  const fileInput = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  // 같은 이유로 이어서 막혀도 매번 알리도록 횟수를 알림의 key로 쓴다
  const [fileError, setFileError] = useState<{ message: string; tries: number } | null>(null)
  const failFile = (message: string | null) => setFileError((e) => (message ? { message, tries: (e?.tries ?? 0) + 1 } : null))

  const take = async (file: File | undefined) => {
    // 읽는 중에 끌어 놓은 파일은 받지 않는다(버튼만 막혀 있어 두 번 읽고 늦게 끝난 쪽이 앞의 결과를 덮는다)
    if (!file || busy) return
    failFile(await onFile(file))
  }

  const drop = (event: DragEvent) => {
    event.preventDefault()
    setDragging(false)
    void take(event.dataTransfer.files[0])
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
      <p className="font-bold">{title}</p>
      <p className="text-label-sm text-text-secondary">{hint}</p>
      <input
        ref={fileInput}
        type="file"
        accept={accept}
        className="sr-only"
        tabIndex={-1}
        aria-hidden="true"
        onChange={(e) => {
          void take(e.target.files?.[0])
          // 같은 파일을 다시 골라도 change가 오도록 비운다
          e.target.value = ""
        }}
      />
      <Button type="button" disabled={busy} aria-describedby={fileError ? noteId(id) : undefined} onClick={() => fileInput.current?.click()}>
        파일 고르기
      </Button>
      {fileError && (
        <div role="alert" key={fileError.tries}>
          <FieldError id={id}>{fileError.message}</FieldError>
        </div>
      )}
    </div>
  )
}
