import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react"
import { useLoaderData, useNavigate, type LoaderFunctionArgs } from "react-router"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Panel } from "@/shared/components/panel"
import { CircleAlertIcon, LockIcon, PlusIcon, XIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { cn, joinClasses } from "@/shared/lib/utils"

import { getCard, suggestKeywords, updateCard } from "../api"
import { STAR_PARTS } from "../card-rules"
import { flaggedValues, SUMMARY_LIMIT } from "../card-text"
import { CardsSubpage, cardsTitle, pressedOutline, sectionTitle } from "../components/card-bits"
import { QUESTION_TYPES, type KeywordSuggestion, type QuestionType } from "../types"

export function cardEditLoader({ params }: LoaderFunctionArgs) {
  return getCard(Number(params.cardId))
}

let factSeq = 0
const withIds = (facts: string[]) => facts.map((text) => ({ id: ++factSeq, text }))

/** 카드 고치기(6차 "카드 편집"). 저장하면 서버가 검사 결과를 다시 매기고 상세로 돌아간다. */
export function CardEditPage() {
  const card = useLoaderData<typeof cardEditLoader>()
  const navigate = useNavigate()
  const [title, setTitle] = useState(card.title)
  const [period, setPeriod] = useState(card.period ?? "")
  const [org, setOrg] = useState(card.org ?? "")
  const [role, setRole] = useState(card.role ?? "")
  const [summary, setSummary] = useState(card.summary)
  const [star, setStar] = useState(card.star)
  const [facts, setFacts] = useState(() => withIds(card.facts))
  const [tags, setTags] = useState(card.tags)
  const [types, setTypes] = useState<QuestionType[]>(card.usableFor)
  const [saving, setSaving] = useState(false)
  const flagged = flaggedValues(star.result)

  async function save(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    try {
      await updateCard(card.id, {
        title: title.trim(),
        period: period.trim() || null,
        org: org.trim() || null,
        role: role.trim() || null,
        summary: summary.trim(),
        star,
        facts: facts.map((f) => f.text.trim()).filter(Boolean),
        tags,
        usableFor: QUESTION_TYPES.filter((t) => types.includes(t)),
      })
      navigate(paths.card(card.id))
    } finally {
      setSaving(false)
    }
  }

  return (
    <CardsSubpage>
      <form onSubmit={save} className="flex flex-col gap-6">
        <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-4">
          <h1 className={cardsTitle}>카드 고치기</h1>
          <div className="flex flex-wrap items-center gap-2">
            <Button type="button" variant="outline" onClick={() => navigate(paths.card(card.id))}>
              취소
            </Button>
            <Button type="submit" className="px-6" disabled={saving}>
              저장
            </Button>
          </div>
        </div>

        <div className="flex flex-wrap items-start gap-6">
          <div className="flex min-w-0 flex-[999_1_560px] flex-col gap-6">
            <Panel aria-labelledby="edit-basic" className="flex flex-col gap-4 p-6">
              <h2 id="edit-basic" className={sectionTitle}>
                기본
              </h2>
              <Field id="edit-title" label="제목">
                <Input id="edit-title" value={title} onChange={(e) => setTitle(e.target.value)} required className="md:text-base" />
              </Field>
              <div className="flex flex-wrap gap-4">
                <Field id="edit-period" label="기간" hint="예: 2025-03 ~ 2025-05, 2026 ~ 현재" className="min-w-0 flex-[1_1_200px]">
                  <Input id="edit-period" value={period} onChange={(e) => setPeriod(e.target.value)} aria-describedby="edit-period-hint" className="md:text-base" />
                </Field>
                <Field id="edit-org" label="어디서" className="min-w-0 flex-[2_1_280px]">
                  <Input id="edit-org" value={org} onChange={(e) => setOrg(e.target.value)} className="md:text-base" />
                </Field>
              </div>
              <Field id="edit-role" label="맡은 일">
                <Input id="edit-role" value={role} onChange={(e) => setRole(e.target.value)} className="md:text-base" />
              </Field>
              <Field
                id="edit-summary"
                label="요약"
                aside={
                  <span className={joinClasses("text-label-sm", summary.length > SUMMARY_LIMIT ? "font-bold text-danger" : "text-text-secondary")}>
                    {summary.length} / {SUMMARY_LIMIT}자
                  </span>
                }
              >
                <Input id="edit-summary" value={summary} onChange={(e) => setSummary(e.target.value)} className="md:text-base" />
              </Field>
            </Panel>

            <Panel aria-labelledby="edit-star" className="flex flex-col gap-4 p-6">
              <h2 id="edit-star" className={sectionTitle}>
                STAR
              </h2>
              {STAR_PARTS.map(([key, label]) => (
                <Field key={key} id={`edit-star-${key}`} label={label}>
                  <Textarea
                    id={`edit-star-${key}`}
                    rows={3}
                    value={star[key]}
                    onChange={(e) => setStar((s) => ({ ...s, [key]: e.target.value }))}
                    className="min-h-24"
                  />
                </Field>
              ))}
              {flagged.length > 0 && (
                <p className="flex items-start gap-1.5 text-label-sm font-medium">
                  <CircleAlertIcon aria-hidden="true" className="mt-px size-4 flex-none text-warning" />
                  <span>결과의 {flagged.map((v) => `[확인 필요: ${v}]`).join(", ")}를 맞는 숫자로 고치거나 지워 주세요.</span>
                </p>
              )}
            </Panel>

            <Panel aria-labelledby="edit-facts" className="flex flex-col gap-3 p-6">
              <h2 id="edit-facts" className={sectionTitle}>
                근거 숫자
              </h2>
              {facts.map((fact, i) => (
                <div key={fact.id} className="flex items-center gap-2">
                  <Input
                    aria-label={`근거 숫자 ${i + 1}`}
                    value={fact.text}
                    onChange={(e) => setFacts((fs) => fs.map((f) => (f.id === fact.id ? { ...f, text: e.target.value } : f)))}
                    className="flex-1 md:text-base"
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={`근거 숫자 ${i + 1} 지우기`}
                    className="text-text-secondary"
                    onClick={() => setFacts((fs) => fs.filter((f) => f.id !== fact.id))}
                  >
                    <XIcon aria-hidden="true" className="size-[18px]" />
                  </Button>
                </div>
              ))}
              <Button type="button" variant="outline" size="sm" className="self-start" onClick={() => setFacts((fs) => [...fs, ...withIds([""])])}>
                <PlusIcon data-icon="inline-start" aria-hidden="true" className="size-4" />
                숫자 더하기
              </Button>
            </Panel>
          </div>

          <aside className="flex min-w-0 flex-[1_1_300px] flex-col gap-6">
            <Panel aria-labelledby="edit-tags" className="flex flex-col gap-3 p-6">
              <h2 id="edit-tags" className={sectionTitle}>
                태그
              </h2>
              <ul className="flex flex-wrap gap-1.5">
                {tags.map((tag) => (
                  <li key={tag} className="inline-flex h-8 items-center gap-0.5 rounded-full bg-surface-muted pr-1 pl-3 text-label-sm font-medium">
                    <span>{tag}</span>
                    <button
                      type="button"
                      aria-label={`${tag} 태그 지우기`}
                      onClick={() => setTags((ts) => ts.filter((t) => t !== tag))}
                      className="inline-flex size-6 cursor-pointer items-center justify-center rounded-full text-text-secondary"
                    >
                      <XIcon aria-hidden="true" className="size-3.5" />
                    </button>
                  </li>
                ))}
              </ul>
              <TagInput onAdd={(tag) => setTags((ts) => (ts.includes(tag) ? ts : [...ts, tag]))} />
            </Panel>

            <Panel aria-labelledby="edit-types" className="flex flex-col gap-3 p-6">
              <h2 id="edit-types" className={sectionTitle}>
                쓸 문항 유형
              </h2>
              <div role="group" aria-labelledby="edit-types" className="flex flex-wrap gap-2">
                {QUESTION_TYPES.map((type) => {
                  const on = types.includes(type)
                  return (
                    <Button
                      key={type}
                      type="button"
                      variant="outline"
                      size="sm"
                      aria-pressed={on}
                      onClick={() => setTypes((ts) => (on ? ts.filter((t) => t !== type) : [...ts, type]))}
                      className={cn(on && pressedOutline)}
                    >
                      {type}
                    </Button>
                  )
                })}
              </div>
            </Panel>

            <Panel aria-labelledby="edit-visibility" className="flex flex-col gap-2 p-6">
              <h2 id="edit-visibility" className={sectionTitle}>
                공개 범위
              </h2>
              <p className="flex items-center gap-1.5 text-sm font-bold">
                <LockIcon aria-hidden="true" className="size-4" />
                <span>나만 보기</span>
              </p>
              <p className="text-label-sm text-text-secondary">지금은 나만 볼 수 있습니다. 그룹에 보이게 하는 것은 아직 없습니다.</p>
            </Panel>
          </aside>
        </div>
      </form>
    </CardsSubpage>
  )
}

/** 이름표와 입력 한 묶음. aside는 이름표 오른쪽(글자 수 등), hint는 입력 아래 작은 글자. */
function Field({ id, label, hint, aside, className, children }: { id: string; label: string; hint?: string; aside?: ReactNode; className?: string; children: ReactNode }) {
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      {aside ? (
        <div className="flex items-baseline justify-between gap-3">
          <label htmlFor={id} className="text-sm font-medium">
            {label}
          </label>
          {aside}
        </div>
      ) : (
        <label htmlFor={id} className="text-sm font-medium">
          {label}
        </label>
      )}
      {children}
      {hint && (
        <p id={`${id}-hint`} className="text-label-sm text-text-secondary">
          {hint}
        </p>
      )}
    </div>
  )
}

/** 태그 더하기: 치는 동안 대표 표기 후보를 보여 주고, 누르거나 Enter로 더한다. */
function TagInput({ onAdd }: { onAdd: (tag: string) => void }) {
  const [text, setText] = useState("")
  const [suggestions, setSuggestions] = useState<KeywordSuggestion[]>([])
  const latest = useRef("")

  useEffect(() => {
    latest.current = text
    if (!text.trim()) return
    void suggestKeywords(text).then((list) => {
      if (latest.current === text) setSuggestions(list)
    })
  }, [text])

  const shown = text.trim() ? suggestions : []
  const add = (tag: string) => {
    if (!tag.trim()) return
    onAdd(tag.trim())
    setText("")
    setSuggestions([])
  }

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor="edit-tag" className="sr-only">
        태그 더하기
      </label>
      <Input
        id="edit-tag"
        value={text}
        autoComplete="off"
        aria-describedby="edit-tag-hint"
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault()
            add(shown[0]?.label ?? text)
          }
        }}
        className="md:text-base"
      />
      {shown.length > 0 && (
        <ul aria-label="태그 후보" className="flex flex-col rounded-control border border-border bg-surface-raised p-1.5 shadow-raised">
          {shown.map((s, i) => (
            <li key={s.label}>
              <button
                type="button"
                onClick={() => add(s.label)}
                className={cn(
                  "flex min-h-control-md w-full cursor-pointer items-center justify-between gap-3 rounded-[12px] px-3 text-left text-sm font-bold",
                  i === 0 ? "bg-accent-subtle" : "hover:bg-surface-muted"
                )}
              >
                <span>{s.label}</span>
                {s.note && <span className="text-xs font-medium text-text-secondary">{s.note}</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
      <p id="edit-tag-hint" className="text-label-sm text-text-secondary">
        다르게 쳐도 대표 표기로 저장합니다.
      </p>
    </div>
  )
}
