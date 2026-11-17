import { useState, type FormEvent } from "react"

import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { memberName } from "@/domain/group/member"
import type { Viewer } from "@/domain/session/types"
import { FieldError, noteId } from "@/shared/components/form-field"
import { Heading } from "@/shared/components/heading"
import { PlaceholderLink } from "@/shared/components/placeholder-link"
import { SurfaceCard } from "@/shared/components/surface-card"
import { textLink } from "@/shared/components/text-link"
import { formatCommentTime } from "@/shared/lib/date"
import { joinClasses } from "@/shared/lib/utils"

import type { Comment } from "../types"

interface CommentsProps {
  comments: Comment[]
  viewer: Viewer
  /** 그룹장이면 남의 댓글도 삭제할 수 있다(리서치 문서: 쓴 사람만 수정·삭제, 그룹장은 삭제만) */
  isLeader: boolean
  /** 댓글을 달거나 고치거나 지운 결과. 화면 읽기에 알린다 */
  notice: string
  onAdd: (text: string) => Promise<void>
  onEdit: (comment: Comment) => void
  onDelete: (comment: Comment) => void
}

const action =
  "min-h-7 cursor-pointer rounded-full px-2 text-label-sm font-semibold text-text-secondary hover:bg-surface-muted hover:text-foreground"

/** 댓글: "그룹 × 공고"에 달리고 이 그룹에만 보인다. 글자와 링크만 받는다. */
export function Comments({ comments, viewer, isLeader, notice, onAdd, onEdit, onDelete }: CommentsProps) {
  const [draft, setDraft] = useState("")
  const [empty, setEmpty] = useState(false)
  const [saving, setSaving] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const text = draft.trim()
    if (saving) return
    if (!text) return setEmpty(true)
    setSaving(true)
    try {
      await onAdd(text)
      setDraft("")
    } finally {
      setSaving(false)
    }
  }

  return (
    <SurfaceCard id="comments" aria-label="댓글" className="flex scroll-mt-4 flex-col px-6 pt-6 pb-5">
      <div className="mb-3 flex items-baseline justify-between gap-3">
        <Heading>댓글</Heading>
        <span className="text-xs text-text-secondary">{comments.length}개 · 이 그룹에만 보임</span>
      </div>
      <ul className="flex flex-col">
        {comments.map((comment) => {
          const mine = comment.author?.id === viewer.id
          return (
            <li key={comment.id} className="flex flex-col gap-1.5 border-t border-border-subtle py-3.5">
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm font-bold">
                  {mine ? "나" : memberName(comment.author)}{" "}
                  <span className="font-normal text-text-secondary">· {formatCommentTime(comment.createdAt)}</span>
                </span>
                {mine ? (
                  <span className="inline-flex items-center gap-0.5">
                    <button type="button" className={action} onClick={() => onEdit(comment)}>
                      수정
                    </button>
                    <button type="button" className={action} onClick={() => onDelete(comment)}>
                      삭제
                    </button>
                  </span>
                ) : (
                  isLeader && (
                    <button type="button" className={action} onClick={() => onDelete(comment)}>
                      삭제
                    </button>
                  )
                )}
              </div>
              <p className="text-sm whitespace-pre-wrap">
                {comment.body.map((part, index) =>
                  part.href === undefined ? (
                    <span key={index}>{part.text}</span>
                  ) : part.href === "#" ? (
                    // shortcut: 예시 데이터의 주소 없는 링크(#)는 자리만 둔다, 서버를 붙이면 이 갈래를 지운다
                    <PlaceholderLink key={index} className={textLink}>
                      {part.text}
                    </PlaceholderLink>
                  ) : (
                    <a
                      key={index}
                      href={part.href}
                      target="_blank"
                      rel="noreferrer ugc"
                      className={joinClasses(textLink, "break-all")}
                    >
                      {part.text}
                    </a>
                  )
                )}
              </p>
            </li>
          )
        })}
      </ul>
      <form onSubmit={submit} className="flex flex-col gap-2 border-t border-border-subtle pt-3.5">
        <label htmlFor="comment-text" className="text-sm font-medium">
          댓글 쓰기
        </label>
        <Textarea
          id="comment-text"
          rows={3}
          placeholder="글자와 링크만 넣을 수 있습니다"
          value={draft}
          aria-invalid={empty || undefined}
          aria-describedby={empty ? noteId("comment-text") : undefined}
          onChange={(event) => {
            setDraft(event.target.value)
            setEmpty(false)
          }}
        />
        {empty && <FieldError id="comment-text">댓글을 넣어 주세요.</FieldError>}
        <Button type="submit" variant="outline" className="self-end" disabled={saving}>
          댓글 달기
        </Button>
        <p role="status" className="sr-only">
          {notice}
        </p>
      </form>
    </SurfaceCard>
  )
}
