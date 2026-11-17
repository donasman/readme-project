import { useEffect, useRef, useState, type ReactNode } from "react"

import SegmentedControl from "@/components/arc/segmented-control/segmented-control"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { DisclosureOptions } from "@/domain/group/disclosure-options"
import type { Disclosure, Group } from "@/domain/group/types"
import { AppDialog, DialogCancel } from "@/shared/components/app-dialog"
import { BusyButton } from "@/shared/components/busy-button"
import { ChoiceInput } from "@/shared/components/choice-input"
import { FieldError, FieldHint, FormField, noteId } from "@/shared/components/form-field"
import { Notice } from "@/shared/components/notice"

import type { Comment, Deadline, EditPostResult, GroupPost, PostEdit } from "../types"

// 그룹 공고 화면의 창들(시안 7차 "창 · …"). 연 쪽이 대상(공고, 댓글)을 넘기고, 대상이 null이면 닫힌다.
// 저장은 연 쪽이 넘긴 함수가 하고, 창은 그 뒤 닫힌다.

const sourceLink = "font-bold whitespace-nowrap text-primary"

interface TargetDialogProps<T> {
  target: T | null
  onClose: () => void
}

const postName = (post: Pick<GroupPost, "company" | "title">) => `${post.company} · ${post.title}`

/** 마감일 넣기: 날짜 하나, 또는 "날짜 없이 상시 채용". 한 번 넣으면 바꿀 수 없다. */
export function DeadlineDialog({ target, onClose, onSave }: TargetDialogProps<GroupPost> & { onSave: (deadline: Deadline) => Promise<void> }) {
  return (
    <AppDialog
      open={target !== null}
      onOpenChange={(open) => !open && onClose()}
      title="마감일 넣기"
      subtitle={target && postName(target)}
      actions={
        <>
          <DialogCancel />
          <Button type="submit" form="deadline-form">
            넣기
          </Button>
        </>
      }
    >
      {target && <DeadlineForm key={target.id} sourceUrl={target.sourceUrl} onSave={onSave} />}
    </AppDialog>
  )
}

function DeadlineForm({ sourceUrl, onSave }: { sourceUrl: string | null; onSave: (deadline: Deadline) => Promise<void> }) {
  const [date, setDate] = useState("")
  const [always, setAlways] = useState(false)
  const [missing, setMissing] = useState(false)

  return (
    <form
      id="deadline-form"
      className="contents"
      onSubmit={(event) => {
        event.preventDefault()
        if (!always && !date) return setMissing(true)
        void onSave(always ? { kind: "always" } : { kind: "date", date })
      }}
    >
      <FormField id="dg-date" label="마감일" note={missing && <FieldError id="dg-date">날짜를 넣거나 상시 채용을 고르세요.</FieldError>}>
        <Input
          id="dg-date"
          type="date"
          value={date}
          disabled={always}
          aria-invalid={missing || undefined}
          aria-describedby={missing ? noteId("dg-date") : undefined}
          onChange={(e) => {
            setDate(e.target.value)
            setMissing(false)
          }}
          className="md:text-base aria-invalid:ring-0"
        />
      </FormField>
      <label className="-my-2 flex min-h-11 cursor-pointer items-center gap-2.5 text-sm">
        <ChoiceInput
          type="checkbox"
          checked={always}
          onChange={(e) => {
            setAlways(e.target.checked)
            setMissing(false)
          }}
        />
        <span>날짜 없이 상시 채용입니다</span>
      </label>
      <p className="text-label-sm text-text-secondary">
        넣은 날짜는 이 공고가 올라간 모든 그룹에 보이고, 한 번 넣으면 바꿀 수 없습니다.{" "}
        {/* 원문 주소가 없는 공고(본문을 붙여넣은 공고)는 갈 곳이 없어 감춘다 */}
        {sourceUrl && (
          <a href={sourceUrl} target="_blank" rel="noreferrer" className={sourceLink}>
            원문 열기
          </a>
        )}
      </p>
    </form>
  )
}

/**
 * 공고 고치기(시안 10차): 회사명, 공고명, 마감. 남과 함께 쓰는 공고인지는 상세에 없어 저장해 봐야 안다.
 * 서버가 막으면 칸을 잠그고 그 까닭과 "닫기"만 보인다. 다시 열어도 그대로다(이 창은 공고 하나의 상세에서만 연다).
 */
export function PostEditDialog({ target, onClose, onSave }: TargetDialogProps<GroupPost> & { onSave: (edit: PostEdit) => Promise<EditPostResult> }) {
  const [shared, setShared] = useState<string | null>(null)
  const closeRef = useRef<HTMLButtonElement>(null)
  // 누른 "저장"이 사라지고 칸이 잠기므로 초점을 남은 "닫기"로 옮긴다
  useEffect(() => {
    if (shared) closeRef.current?.focus()
  }, [shared])
  return (
    <AppDialog
      open={target !== null}
      onOpenChange={(open) => !open && onClose()}
      title="공고 고치기"
      subtitle="회사명, 공고명, 마감을 고칩니다. 원문 링크는 고칠 수 없습니다."
      actions={
        shared ? (
          <DialogCancel ref={closeRef}>닫기</DialogCancel>
        ) : (
          <>
            <DialogCancel />
            <Button type="submit" form="post-edit-form">
              저장
            </Button>
          </>
        )
      }
    >
      {target && (
        <PostEditForm
          key={target.id}
          post={target}
          locked={shared !== null}
          onSave={async (edit) => {
            const result = await onSave(edit)
            if (!result.ok) setShared(result.message)
          }}
        />
      )}
      {shared ? (
        <Notice title="고칠 수 없는 공고입니다">
          <span>{shared}. 회사명, 공고명, 마감은 이 공고를 올린 그룹들이 함께 보는 값입니다.</span>
        </Notice>
      ) : (
        <p className="text-label-sm text-text-secondary">고친 값은 이 공고를 보는 멤버 모두에게 보입니다. 내가 다른 그룹에도 올린 공고면 그 그룹에도 보입니다.</p>
      )}
    </AppDialog>
  )
}

type DueMode = Deadline["kind"]
/** "모름"은 아직 모르는 공고에만 둔다(서버가 되돌리기를 받지 않는다) */
const DUE_MODES: { value: DueMode; label: string }[] = [
  { value: "date", label: "날짜" },
  { value: "always", label: "상시" },
  { value: "unknown", label: "모름" },
]
type EditField = "company" | "title" | "deadline"

function PostEditForm({ post, locked, onSave }: { post: GroupPost; locked: boolean; onSave: (edit: PostEdit) => Promise<void> }) {
  const wasUnknown = post.deadline.kind === "unknown"
  const [company, setCompany] = useState(post.company)
  const [title, setTitle] = useState(post.title)
  const [dueMode, setDueMode] = useState<DueMode>(post.deadline.kind)
  const [dueDate, setDueDate] = useState(post.deadline.kind === "date" ? post.deadline.date : "")
  const [errors, setErrors] = useState<Partial<Record<EditField, string>>>({})
  const clear = (key: EditField) => setErrors((er) => ({ ...er, [key]: undefined }))

  // 서버가 받는 길이(공고 문서 4.5의 고치기 요청)
  const text = (key: "company" | "title", label: string, value: string, set: (v: string) => void, maxLength: number) => (
    <FormField id={`pe-${key}`} label={label} note={errors[key] && <FieldError id={`pe-${key}`}>{errors[key]}</FieldError>}>
      <Input
        id={`pe-${key}`}
        value={value}
        maxLength={maxLength}
        aria-invalid={errors[key] ? true : undefined}
        aria-describedby={errors[key] ? noteId(`pe-${key}`) : undefined}
        onChange={(e) => {
          set(e.target.value)
          clear(key)
        }}
        className="md:text-base aria-invalid:ring-0"
      />
    </FormField>
  )

  let dueNote: ReactNode = null
  if (errors.deadline) dueNote = <FieldError id="pe-due">{errors.deadline}</FieldError>
  else if (wasUnknown) dueNote = <FieldHint id="pe-due">마감일을 아직 모르는 공고입니다. 날짜나 상시로 저장하면 다시 &quot;모름&quot;으로 돌릴 수 없습니다.</FieldHint>

  return (
    <form
      id="post-edit-form"
      noValidate
      className="contents"
      onSubmit={(event) => {
        event.preventDefault()
        const next: Partial<Record<EditField, string>> = {}
        if (!company.trim()) next.company = "회사명을 넣어 주세요."
        if (!title.trim()) next.title = "공고명을 넣어 주세요."
        if (dueMode === "date" && !dueDate) next.deadline = wasUnknown ? "마감일을 넣거나 상시, 모름을 고르세요." : "마감일을 넣거나 상시를 고르세요."
        setErrors(next)
        if (Object.keys(next).length) return
        void onSave({ company: company.trim(), title: title.trim(), deadline: dueMode === "date" ? { kind: "date", date: dueDate } : { kind: dueMode } })
      }}
    >
      {/* 함께 쓰는 공고로 막히면 칸을 모두 잠근다 */}
      <fieldset disabled={locked} className="flex min-w-0 flex-col gap-5">
        {text("company", "회사명", company, setCompany, 200)}
        {text("title", "공고명", title, setTitle, 300)}
        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-medium">마감</span>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
            <SegmentedControl
              label="마감"
              value={dueMode}
              onValueChange={(value) => {
                setDueMode(value as DueMode)
                clear("deadline")
              }}
              options={wasUnknown ? DUE_MODES : DUE_MODES.slice(0, 2)}
            />
            {dueMode === "date" && (
              <>
                <label htmlFor="pe-due" className="sr-only">
                  마감일
                </label>
                <Input
                  id="pe-due"
                  type="date"
                  value={dueDate}
                  aria-invalid={errors.deadline ? true : undefined}
                  aria-describedby={dueNote ? noteId("pe-due") : undefined}
                  onChange={(e) => {
                    setDueDate(e.target.value)
                    clear("deadline")
                  }}
                  className="max-w-[220px] flex-[1_1_180px] md:text-base aria-invalid:ring-0"
                />
              </>
            )}
          </div>
          {dueNote}
        </div>
      </fieldset>
    </form>
  )
}

/** 그룹에서 내리기: 댓글과 관심은 남고, 다시 올리면 돌아온다. */
export function TakedownDialog({ target, onClose, onConfirm }: TargetDialogProps<GroupPost> & { onConfirm: () => Promise<void> }) {
  return (
    <AppDialog
      open={target !== null}
      onOpenChange={(open) => !open && onClose()}
      title="그룹에서 내릴까요?"
      subtitle={target && postName(target)}
      actions={
        <>
          <DialogCancel />
          <BusyButton type="button" variant="danger" onClick={onConfirm}>
            내리기
          </BusyButton>
        </>
      }
    >
      <p className="text-sm">
        이 그룹의 공고 목록에서 빠집니다. {target && target.commentCount > 0 ? `댓글 ${target.commentCount}개와 관심 표시는` : "관심 표시는"} 지워지지 않고, 같은
        공고를 다시 올리면 그대로 돌아옵니다.
      </p>
    </AppDialog>
  )
}

const commentText = (comment: Comment) => comment.body.map((part) => part.text).join("")

/** 댓글 고치기: 글자와 링크만. */
export function CommentEditDialog({ target, onClose, onSave }: TargetDialogProps<Comment> & { onSave: (text: string) => Promise<void> }) {
  return (
    <AppDialog
      open={target !== null}
      onOpenChange={(open) => !open && onClose()}
      title="댓글 고치기"
      actions={
        <>
          <DialogCancel />
          <Button type="submit" form="comment-edit-form">
            저장
          </Button>
        </>
      }
    >
      {target && <CommentEditForm key={target.id} initial={commentText(target)} onSave={onSave} />}
    </AppDialog>
  )
}

function CommentEditForm({ initial, onSave }: { initial: string; onSave: (text: string) => Promise<void> }) {
  const [text, setText] = useState(initial)
  const [empty, setEmpty] = useState(false)
  return (
    <form
      id="comment-edit-form"
      className="flex flex-col gap-1.5"
      onSubmit={(event) => {
        event.preventDefault()
        if (!text.trim()) return setEmpty(true)
        void onSave(text)
      }}
    >
      <label htmlFor="dg-comment" className="sr-only">
        댓글
      </label>
      <Textarea
        id="dg-comment"
        rows={4}
        value={text}
        aria-invalid={empty || undefined}
        aria-describedby={noteId("dg-comment")}
        onChange={(e) => {
          setText(e.target.value)
          setEmpty(false)
        }}
        className="min-h-24"
      />
      {empty ? <FieldError id="dg-comment">댓글을 비워 둘 수 없습니다. 지우려면 삭제를 누르세요.</FieldError> : <FieldHint id="dg-comment">글자와 링크만 넣을 수 있습니다.</FieldHint>}
    </form>
  )
}

/** 댓글 지우기: 지울 글을 보여 주고 되돌릴 수 없다고 알린다. */
export function CommentDeleteDialog({ target, onClose, onConfirm }: TargetDialogProps<Comment> & { onConfirm: () => Promise<void> }) {
  return (
    <AppDialog
      open={target !== null}
      onOpenChange={(open) => !open && onClose()}
      title="댓글을 지울까요?"
      actions={
        <>
          <DialogCancel />
          <BusyButton type="button" variant="danger" onClick={onConfirm}>
            지우기
          </BusyButton>
        </>
      }
    >
      <p className="rounded-control bg-surface-muted px-4 py-3 text-sm whitespace-pre-wrap">{target && commentText(target)}</p>
      <p className="text-sm text-text-secondary">지우면 되돌릴 수 없습니다.</p>
    </AppDialog>
  )
}

/** 공개 범위: 이 그룹 멤버에게 내 지원 상태를 어디까지 보일지. 저장해야 바뀐다. */
export function DisclosureDialog({ target, onClose, onSave }: TargetDialogProps<Group> & { onSave: (disclosure: Disclosure) => Promise<void> }) {
  return (
    <AppDialog
      open={target !== null}
      onOpenChange={(open) => !open && onClose()}
      title="공개 범위"
      titleId="disclosure-title"
      subtitle={target && `${target.name} 멤버에게 내 지원 상태를 어디까지 보일지 정합니다.`}
      actions={
        <>
          <DialogCancel />
          <Button type="submit" form="disclosure-form">
            저장
          </Button>
        </>
      }
    >
      {target && <DisclosureForm key={target.id} initial={target.myDisclosure} onSave={onSave} />}
    </AppDialog>
  )
}

function DisclosureForm({ initial, onSave }: { initial: Disclosure; onSave: (disclosure: Disclosure) => Promise<void> }) {
  const [value, setValue] = useState(initial)
  return (
    <form
      id="disclosure-form"
      className="contents"
      onSubmit={(event) => {
        event.preventDefault()
        void onSave(value)
      }}
    >
      <DisclosureOptions value={value} onChange={setValue} labelledBy="disclosure-title" />
      <p className="text-label-sm text-text-secondary">관심을 켠 공고에만 적용됩니다. 그룹마다 따로 정합니다.</p>
    </form>
  )
}
