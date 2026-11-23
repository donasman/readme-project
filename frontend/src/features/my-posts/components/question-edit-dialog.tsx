import { useState, type FormEvent } from "react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { AppDialog, DialogCancel } from "@/shared/components/app-dialog"
import { FieldError, FormField, noteId } from "@/shared/components/form-field"
import { stepButton } from "@/shared/components/step-button"
import { ArrowDownIcon, ArrowUpIcon, PlusIcon, XIcon } from "@/shared/icons"

import { moveBy } from "../card-priority"
import { limitError, MAX_QUESTIONS, QUESTION_TEXT_MAX, textError, toLimit } from "../question-match"
import type { Question } from "../types"

/** 창의 한 줄. 글자 수는 칸의 글 그대로 두고 저장할 때 숫자로 바꾼다 */
interface Row {
  key: number
  id: string | null
  text: string
  limit: string
}

interface QuestionEditDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  questions: Question[]
  /** 공고 분석의 문항을 아직 저장하지 않았다 */
  unsaved: boolean
  onSave: (list: Pick<Question, "id" | "text" | "limit">[]) => Promise<void>
}

/**
 * 문항 고치기 창(문항 매칭 문서 5절, 시안 없음): 문항 목록 전체의 글과 글자 수를 고치고, 더하고, 빼고, 순서를 바꾼다.
 * 저장하면 목록을 통째로 둔다. 남은 문항은 고른 카드와 유형이 남는다. 문항은 10개, 글은 1,000자까지, 글자 수는 1~10,000이나 비움.
 */
export function QuestionEditDialog({ open, onOpenChange, questions, unsaved, onSave }: QuestionEditDialogProps) {
  const [rows, setRows] = useState<Row[]>(() => questions.map((q, i) => ({ key: i, id: q.id, text: q.text, limit: q.limit === null ? "" : String(q.limit) })))
  const [nextKey, setNextKey] = useState(questions.length)
  // 오류 글은 저장을 누른 뒤부터 보인다
  const [checked, setChecked] = useState(false)
  const [saving, setSaving] = useState(false)
  const errors = rows.map((r) => ({ text: textError(r.text), limit: limitError(r.limit) }))
  const isNew = questions.length === 0

  const change = (key: number, patch: Partial<Row>) => setRows(rows.map((r) => (r.key === key ? { ...r, ...patch } : r)))

  async function submit(event: FormEvent) {
    event.preventDefault()
    setChecked(true)
    if (errors.some((e) => e.text || e.limit)) return
    setSaving(true)
    try {
      await onSave(rows.map((r) => ({ id: r.id, text: r.text.trim(), limit: toLimit(r.limit) })))
    } finally {
      setSaving(false)
    }
  }

  return (
    <AppDialog
      open={open}
      onOpenChange={onOpenChange}
      placement="top"
      title={isNew ? "문항 넣기" : "문항 고치기"}
      subtitle={unsaved ? "공고에서 찾은 문항입니다. 저장하면 이 목록을 씁니다." : undefined}
      actions={
        <>
          <DialogCancel />
          <Button type="submit" form="question-edit" disabled={saving}>
            저장
          </Button>
        </>
      }
    >
      <form id="question-edit" noValidate onSubmit={submit} className="contents">
        <p className="text-label-sm text-text-secondary">
          문항은 {MAX_QUESTIONS}개, 글은 {QUESTION_TEXT_MAX.toLocaleString("ko-KR")}자까지 넣을 수 있습니다. 글자 수가 없으면 비워 둡니다.
        </p>
        {rows.length === 0 && !isNew && <p className="text-sm text-text-secondary">문항이 없습니다. 저장하면 이 공고의 문항을 모두 뺍니다.</p>}
        {rows.length > 0 && (
          <ol className="flex flex-col gap-5">
            {rows.map((row, i) => {
              const textId = `qe-text-${row.key}`
              const limitId = `qe-limit-${row.key}`
              const error = checked ? errors[i] : { text: null, limit: null }
              return (
                <li key={row.key} className="flex flex-col gap-3 border-t border-border-subtle pt-5 first:border-t-0 first:pt-0">
                  <FormField
                    id={textId}
                    label={`${i + 1}번 문항`}
                    aside={
                      <div className="-my-1.5 flex items-center">
                        <Button type="button" variant="ghost" size="icon-sm" aria-label={`${i + 1}번 문항 위로`} disabled={i === 0} focusableWhenDisabled className={stepButton} onClick={() => setRows(moveBy(rows, i, -1))}>
                          <ArrowUpIcon aria-hidden="true" />
                        </Button>
                        <Button type="button" variant="ghost" size="icon-sm" aria-label={`${i + 1}번 문항 아래로`} disabled={i === rows.length - 1} focusableWhenDisabled className={stepButton} onClick={() => setRows(moveBy(rows, i, 1))}>
                          <ArrowDownIcon aria-hidden="true" />
                        </Button>
                        <Button type="button" variant="ghost" size="icon-sm" aria-label={`${i + 1}번 문항 빼기`} onClick={() => setRows(rows.filter((r) => r.key !== row.key))}>
                          <XIcon aria-hidden="true" />
                        </Button>
                      </div>
                    }
                    note={error.text && <FieldError id={textId}>{error.text}</FieldError>}
                  >
                    <Textarea
                      id={textId}
                      rows={3}
                      maxLength={QUESTION_TEXT_MAX}
                      value={row.text}
                      aria-invalid={!!error.text || undefined}
                      aria-describedby={error.text ? noteId(textId) : undefined}
                      onChange={(e) => change(row.key, { text: e.target.value })}
                    />
                  </FormField>
                  <FormField
                    id={limitId}
                    label={
                      <>
                        <span className="sr-only">{i + 1}번 문항 </span>글자 수
                      </>
                    }
                    note={error.limit && <FieldError id={limitId}>{error.limit}</FieldError>}>
                    <Input
                      id={limitId}
                      // number 칸은 틀린 글(-, 1e)을 빈 값으로 넘겨 글자 수 없음으로 저장된다. 글로 받아 검사한다
                      type="text"
                      inputMode="numeric"
                      placeholder="없음"
                      value={row.limit}
                      aria-invalid={!!error.limit || undefined}
                      aria-describedby={error.limit ? noteId(limitId) : undefined}
                      onChange={(e) => change(row.key, { limit: e.target.value })}
                      className="max-w-40"
                    />
                  </FormField>
                </li>
              )
            })}
          </ol>
        )}
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="self-start"
          disabled={rows.length >= MAX_QUESTIONS}
          onClick={() => {
            setRows([...rows, { key: nextKey, id: null, text: "", limit: "" }])
            setNextKey(nextKey + 1)
          }}
        >
          <PlusIcon data-icon="inline-start" aria-hidden="true" />
          문항 더하기
        </Button>
      </form>
    </AppDialog>
  )
}
