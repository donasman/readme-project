import { useState, type FormEvent } from "react"

import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import type { Viewer } from "@/domain/session/types"
import { PlaceholderLink } from "@/shared/components/placeholder-link"
import { formatCommentTime } from "@/shared/lib/date"

import type { Comment } from "../types"

interface CommentsProps {
  comments: Comment[]
  viewer: Viewer
  /** 그룹장이면 남의 댓글도 삭제할 수 있다(리서치 문서: 쓴 사람만 수정·삭제, 그룹장은 삭제만) */
  isLeader: boolean
  onAdd: (text: string) => Promise<void>
}

const action =
  "min-h-7 cursor-pointer rounded-full px-2 text-label-sm font-semibold text-text-secondary hover:bg-surface-muted hover:text-foreground"

/** 댓글: "그룹 × 공고"에 달리고 이 그룹에만 보인다. 글자와 링크만 받는다. */
export function Comments({ comments, viewer, isLeader, onAdd }: CommentsProps) {
  const [draft, setDraft] = useState("")
  const [saving, setSaving] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const text = draft.trim()
    if (!text || saving) return
    setSaving(true)
    try {
      await onAdd(text)
      setDraft("")
    } finally {
      setSaving(false)
    }
  }

  return (
    <section
      id="comments"
      aria-label="댓글"
      className="flex scroll-mt-4 flex-col rounded-surface border border-border bg-surface px-6 pt-6 pb-5 shadow-resting"
    >
      <div className="mb-3 flex items-baseline justify-between gap-3">
        <h2 className="font-heading text-xl leading-display font-bold tracking-display">댓글</h2>
        <span className="text-xs text-text-secondary">{comments.length}개 · 이 그룹에만 보임</span>
      </div>
      <ul className="flex flex-col">
        {comments.map((comment) => {
          const mine = comment.author.id === viewer.id
          return (
            <li key={comment.id} className="flex flex-col gap-1.5 border-t border-border-subtle py-3.5">
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm font-bold">
                  {mine ? "나" : comment.author.name}{" "}
                  <span className="font-normal text-text-secondary">· {formatCommentTime(comment.createdAt)}</span>
                </span>
                {/* 수정·삭제: 아직 동작 없음 */}
                {mine ? (
                  <span className="inline-flex items-center gap-0.5">
                    <button type="button" className={action}>
                      수정
                    </button>
                    <button type="button" className={action}>
                      삭제
                    </button>
                  </span>
                ) : (
                  isLeader && (
                    <button type="button" className={action}>
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
                    <PlaceholderLink key={index} className="font-bold text-primary underline">
                      {part.text}
                    </PlaceholderLink>
                  ) : (
                    <a
                      key={index}
                      href={part.href}
                      target="_blank"
                      rel="noreferrer ugc"
                      className="font-bold break-all text-primary underline"
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
          onChange={(event) => setDraft(event.target.value)}
        />
        <Button type="submit" variant="outline" className="self-end" disabled={saving}>
          댓글 달기
        </Button>
      </form>
    </section>
  )
}
