import { useState } from "react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { DisclosureOptions } from "@/domain/group/disclosure-options"
import type { Disclosure, Group } from "@/domain/group/types"
import { AppDialog, DialogCancel } from "@/shared/components/app-dialog"
import { FieldError, FieldHint, FormField, noteId } from "@/shared/components/form-field"
import { PlaceholderLink } from "@/shared/components/placeholder-link"

import type { Comment, Deadline, GroupPost } from "../types"

// 그룹 공고 화면의 창들(시안 7차 "창 · …"). 연 쪽이 대상(공고, 댓글)을 넘기고, 대상이 null이면 닫힌다.
// 저장은 연 쪽이 넘긴 함수가 하고, 창은 그 뒤 닫힌다.

const sourceLink = "font-bold whitespace-nowrap text-primary"

interface TargetDialogProps<T> {
  target: T | null
  onClose: () => void
}

const postName = (post: Pick<GroupPost, "company" | "title">) => `${post.company} · ${post.title}`

/** 마감일 넣기: 날짜 하나, 또는 "날짜 없이 상시 채용". 한 번 넣으면 바꿀 수 없다. */
export function DeadlineDialog({ target, onClose, onSave }: TargetDialogProps<GroupPost & { sourceUrl?: string | null }> & { onSave: (deadline: Deadline) => Promise<void> }) {
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
      {target && <DeadlineForm key={target.id} sourceUrl={target.sourceUrl ?? null} onSave={onSave} />}
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
        <input
          type="checkbox"
          checked={always}
          onChange={(e) => {
            setAlways(e.target.checked)
            setMissing(false)
          }}
          className="size-[18px] flex-none accent-primary"
        />
        <span>날짜 없이 상시 채용입니다</span>
      </label>
      <p className="text-label-sm text-text-secondary">
        넣은 날짜는 이 공고가 올라간 모든 그룹에 보이고, 한 번 넣으면 바꿀 수 없습니다.{" "}
        {sourceUrl ? (
          <a href={sourceUrl} target="_blank" rel="noreferrer" className={sourceLink}>
            원문 열기
          </a>
        ) : (
          // 예시 데이터에 원문 주소가 없는 공고: 자리만 둔다
          <PlaceholderLink className={sourceLink}>원문 열기</PlaceholderLink>
        )}
      </p>
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
          <Button type="button" variant="danger" onClick={() => void onConfirm()}>
            내리기
          </Button>
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
          <Button type="button" variant="danger" onClick={() => void onConfirm()}>
            지우기
          </Button>
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
