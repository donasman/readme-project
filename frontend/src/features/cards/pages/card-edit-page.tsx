import { useEffect, useState, type FormEvent } from "react"
import { useLoaderData, useNavigate, type LoaderFunctionArgs } from "react-router"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { getCard } from "@/domain/card/api"
import { isOptionalPart, STORY_PARTS } from "@/domain/card/card-rules"
import type { QuestionType } from "@/domain/card/types"
import { sectionTitle, subpageTitle } from "@/shared/components/heading"
import { FieldError, FieldHint, FormField as Field, noteId } from "@/shared/components/form-field"
import { IconNote } from "@/shared/components/notice"
import { columns, mainColumn, sideColumn } from "@/shared/components/page-layout"
import { Panel } from "@/shared/components/panel"
import { ToggleButton } from "@/shared/components/toggle-button"
import { LockIcon, PlusIcon, XIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { cn, docTitle, joinClasses } from "@/shared/lib/utils"

import { createCard, getCards, suggestKeywords, updateCard } from "../api"
import { experienceNames } from "../card-rules"
import { storyFlaggedValues, SUMMARY_LIMIT } from "../card-text"
import { CardsSubpage } from "../components/card-bits"
import { QUESTION_TYPES, type CardInput, type KeywordSuggestion } from "../types"

// 칸의 maxLength는 서버가 받는 길이다(경험카드 문서 4.3). 넘으면 서버가 400으로 거절한다

/** 고치기는 그 카드를, 직접 만들기는 빈 카드를 같은 화면에 연다. id가 null이면 새 카드다. */
type EditTarget = CardInput & { id: string | null }

/** 고칠 카드와 이미 쓴 경험 이름(경험 이름 칸의 고르기 목록). 이름은 목록이 받은 카드에서 만든다(경험카드 문서 4.8) */
interface EditData {
  card: EditTarget
  experiences: string[]
}

export async function cardEditLoader({ params }: LoaderFunctionArgs): Promise<EditData> {
  const [card, { cards }] = await Promise.all([getCard(params.cardId ?? ""), getCards()])
  return { card, experiences: experienceNames(cards) }
}

/** 직접 만들기. ?experience= 로 오면("장면 더하기") 그 경험의 새 장면이다 */
export async function cardNewLoader({ request }: LoaderFunctionArgs): Promise<EditData> {
  const experience = new URL(request.url).searchParams.get("experience") ?? ""
  const { cards } = await getCards()
  const star = { situation: "", task: "", obstacle: "", action: "", result: "", learning: "" }
  return { card: { id: null, experience, title: "", period: null, org: null, role: null, summary: "", tags: [], usableFor: [], star, facts: [] }, experiences: experienceNames(cards) }
}

/** 선택 칸의 안내(무엇을 적는 칸인지) */
const PART_HINTS: Partial<Record<keyof CardInput["star"], string>> = {
  obstacle: "일을 어렵게 만든 조건: 시간, 운영 중인 서비스, 의견이 다른 사람 …",
  learning: "이 일로 바뀐 생각이나 일하는 방식",
}

let factSeq = 0
const withIds = (facts: string[]) => facts.map((text) => ({ id: ++factSeq, text }))

/** 카드 고치기(6차 "카드 편집")와 직접 만들기. 저장하면 서버가 검사 결과를 매기고 그 카드의 상세로 간다. */
export function CardEditPage() {
  const { card, experiences } = useLoaderData<typeof cardNewLoader>()
  // 취소하면 고치던 카드로, 새 카드면 목록으로 돌아간다
  const back = card.id === null ? paths.cards() : paths.card(card.id)
  const navigate = useNavigate()
  const [experience, setExperience] = useState(card.experience)
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
  const [titleEmpty, setTitleEmpty] = useState(false)
  const flagged = storyFlaggedValues(star)

  async function save(event: FormEvent) {
    event.preventDefault()
    // required는 공백만 넣은 제목을 통과시킨다
    if (!title.trim()) return setTitleEmpty(true)
    setSaving(true)
    try {
      const input: CardInput = {
        experience: experience.trim(),
        title: title.trim(),
        period: period.trim() || null,
        org: org.trim() || null,
        role: role.trim() || null,
        summary: summary.trim(),
        star,
        facts: facts.map((f) => f.text.trim()).filter(Boolean),
        tags,
        usableFor: QUESTION_TYPES.filter((t) => types.includes(t)),
      }
      const saved = card.id === null ? await createCard(input) : await updateCard(card.id, input)
      navigate(paths.card(saved.id))
    } finally {
      setSaving(false)
    }
  }

  return (
    <CardsSubpage>
      <title>{docTitle(card.id === null ? "카드 만들기" : "카드 고치기", "경험카드")}</title>
      <form onSubmit={save} className="flex flex-col gap-6">
        <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-4">
          <h1 className={subpageTitle}>{card.id === null ? "카드 만들기" : "카드 고치기"}</h1>
          <div className="flex flex-wrap items-center gap-2">
            <Button type="button" variant="outline" onClick={() => navigate(back)}>
              취소
            </Button>
            <Button type="submit" className="px-6" disabled={saving}>
              저장
            </Button>
          </div>
        </div>

        <div className={columns}>
          <div className={joinClasses(mainColumn, "gap-6")}>
            <Panel aria-labelledby="edit-basic" className="flex flex-col gap-4 p-6">
              <h2 id="edit-basic" className={sectionTitle}>
                기본
              </h2>
              <Field
                id="edit-experience"
                label="경험 이름"
                aside={<span className="text-label-sm text-text-secondary">선택</span>}
                note={<FieldHint id="edit-experience">같은 프로젝트·업무의 장면 카드는 같은 이름으로 묶입니다. 이미 쓴 이름은 골라 넣을 수 있습니다.</FieldHint>}
              >
                <Input
                  id="edit-experience"
                  list="edit-experience-names"
                  maxLength={200}
                  value={experience}
                  onChange={(e) => setExperience(e.target.value)}
                  aria-describedby={noteId("edit-experience")}
                  className="md:text-base"
                />
                <datalist id="edit-experience-names">
                  {experiences.map((name) => (
                    <option key={name} value={name} />
                  ))}
                </datalist>
              </Field>
              <Field id="edit-title" label="제목" note={titleEmpty && <FieldError id="edit-title">제목을 넣어 주세요.</FieldError>}>
                <Input
                  id="edit-title"
                  maxLength={200}
                  value={title}
                  onChange={(e) => {
                    setTitle(e.target.value)
                    setTitleEmpty(false)
                  }}
                  required
                  aria-invalid={titleEmpty || undefined}
                  aria-describedby={titleEmpty ? noteId("edit-title") : undefined}
                  className="md:text-base"
                />
              </Field>
              <div className="flex flex-wrap gap-4">
                <Field id="edit-period" label="기간" note={<FieldHint id="edit-period">예: 2025-03 ~ 2025-05, 2026 ~ 현재</FieldHint>} className="min-w-0 flex-[1_1_200px]">
                  <Input id="edit-period" maxLength={50} value={period} onChange={(e) => setPeriod(e.target.value)} aria-describedby={noteId("edit-period")} className="md:text-base" />
                </Field>
                <Field id="edit-org" label="어디서" className="min-w-0 flex-[2_1_280px]">
                  <Input id="edit-org" maxLength={200} value={org} onChange={(e) => setOrg(e.target.value)} className="md:text-base" />
                </Field>
              </div>
              <Field id="edit-role" label="맡은 일">
                <Input id="edit-role" maxLength={200} value={role} onChange={(e) => setRole(e.target.value)} className="md:text-base" />
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
                <Input id="edit-summary" maxLength={200} value={summary} onChange={(e) => setSummary(e.target.value)} className="md:text-base" />
              </Field>
            </Panel>

            <Panel aria-labelledby="edit-star" className="flex flex-col gap-4 p-6">
              <h2 id="edit-star" className={sectionTitle}>
                이야기
              </h2>
              {STORY_PARTS.map(([key, label]) => {
                const optional = isOptionalPart(key)
                return (
                  <Field
                    key={key}
                    id={`edit-star-${key}`}
                    label={label}
                    aside={optional && <span className="text-label-sm text-text-secondary">선택</span>}
                    note={optional && <FieldHint id={`edit-star-${key}`}>{PART_HINTS[key]}</FieldHint>}
                  >
                    <Textarea
                      id={`edit-star-${key}`}
                      rows={3}
                      maxLength={2000}
                      value={star[key]}
                      onChange={(e) => setStar((s) => ({ ...s, [key]: e.target.value }))}
                      aria-describedby={optional ? noteId(`edit-star-${key}`) : undefined}
                      className="min-h-24"
                    />
                  </Field>
                )
              })}
              {flagged.length > 0 && (
                <IconNote tone="warning">{flagged.map((v) => `[확인 필요: ${v}]`).join(", ")}를 맞는 숫자로 고치거나 지워 주세요.</IconNote>
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
                    maxLength={500}
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

          <aside className={joinClasses(sideColumn(300), "gap-6")}>
            <Panel aria-labelledby="edit-tags" className="flex flex-col gap-3 p-6">
              <h2 id="edit-tags" className={sectionTitle}>
                태그
              </h2>
              <ul className="flex flex-wrap gap-1.5">
                {tags.map((tag) => (
                  <li key={tag} className="inline-flex h-8 items-center gap-0.5 rounded-full bg-surface-muted pr-1 pl-3 text-label-sm font-medium max-sm:h-control-md max-sm:pr-0">
                    <span>{tag}</span>
                    <button
                      type="button"
                      aria-label={`${tag} 태그 지우기`}
                      onClick={() => setTags((ts) => ts.filter((t) => t !== tag))}
                      className="inline-flex size-6 cursor-pointer items-center justify-center rounded-full text-text-secondary max-sm:size-control-md"
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
                    <ToggleButton key={type} pressed={on} onClick={() => setTypes((ts) => (on ? ts.filter((t) => t !== type) : [...ts, type]))}>
                      {type}
                    </ToggleButton>
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

/** 태그 더하기: 치는 동안 대표 표기 후보를 보여 주고, 누르거나 Enter로 더한다. */
function TagInput({ onAdd }: { onAdd: (tag: string) => void }) {
  const [text, setText] = useState("")
  // 후보와 그 후보를 물은 글자. 글자가 바뀌면 답이 오기 전까지 후보를 보이지 않는다(Enter가 앞 글자의 후보를 더하지 않게)
  const [hint, setHint] = useState<{ q: string; list: KeywordSuggestion[] } | null>(null)

  useEffect(() => {
    if (!text.trim()) return
    let stale = false
    void suggestKeywords(text).then((list) => {
      if (!stale) setHint({ q: text, list })
    })
    return () => {
      stale = true
    }
  }, [text])

  const shown = text.trim() && hint?.q === text ? hint.list : []
  const add = (tag: string) => {
    if (!tag.trim()) return
    onAdd(tag.trim())
    setText("")
    setHint(null)
  }

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor="edit-tag" className="sr-only">
        태그 더하기
      </label>
      <Input
        id="edit-tag"
        maxLength={100}
        value={text}
        autoComplete="off"
        aria-describedby="edit-tag-hint"
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          // 한글 조합 중의 Enter는 글자를 확정할 뿐이다. 그때 더하면 마지막 글자가 칸에 남는다
          if (e.key === "Enter" && !e.nativeEvent.isComposing) {
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
