import { useState, type FormEvent, type ReactNode } from "react"
import { Link } from "react-router"

import SegmentedControl from "@/components/arc/segmented-control/segmented-control"
import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { BackLink } from "@/shared/components/back-link"
import { Chip } from "@/shared/components/chip"
import { FieldError, FieldHint, FormField, noteId } from "@/shared/components/form-field"
import { sectionTitle, subpageTitle } from "@/shared/components/heading"
import { IconNote } from "@/shared/components/notice"
import { Page } from "@/shared/components/page"
import { columns, mainColumn, sideColumn } from "@/shared/components/page-layout"
import { Panel } from "@/shared/components/panel"
import { objectParticle } from "@/shared/lib/korean"
import { isHttpUrl, joinClasses } from "@/shared/lib/utils"

import { sourceParagraphs } from "./post-source"
import type { Deadline, DraftField, PostDraft, PostUpload } from "./types"

type DueMode = Deadline["kind"]
const DUE_MODES: { value: DueMode; label: string }[] = [
  { value: "date", label: "날짜" },
  { value: "always", label: "상시" },
  { value: "unknown", label: "모름" },
]
/** 서버가 받는 칸의 길이(공고 문서 4.4의 올리기 요청) */
const MAX_LENGTH = { company: 200, title: 300, sourceUrl: 2000, applyAt: 100 }
const FIELD_NAMES: Record<DraftField, string> = { company: "회사명", title: "공고명", deadline: "마감일", sourceUrl: "원문 링크", applyAt: "지원하는 곳" }

/** 읽지 못한 칸: 하이라이트 테두리와 옅은 바탕. */
const unreadInput = "border-2 border-highlight-strong bg-highlight-subtle dark:bg-highlight-subtle"

interface PostCheckFormProps {
  draft: PostDraft
  /** 읽어 오기 화면. 위의 돌아가기와 아래의 취소가 간다 */
  backTo: string
  /** 돌아가기 글자(읽어 오기 화면의 이름) */
  backLabel: string
  /** 보내기 버튼 글자 */
  submitLabel: string
  /** "더 있으면 좋은 칸"의 안내 */
  optionalNote: string
  /** 마감을 읽지 못했을 때 안내 끝에 붙이는 문장: "모름"으로 보내면 어디에 보이는지 */
  unknownNote: string
  /** 폼 맨 위의 알림(그룹에 이미 있는 공고 등) */
  notice?: ReactNode
  /** 확인한 값을 보낸다. 끝나면 부르는 화면이 옮기거나 알린다 */
  onSubmit: (upload: PostUpload) => Promise<void>
}

/**
 * 칸 확인(시안 7차, 10차 "내 공고 넣기"): 읽어 온 칸을 고치고 보낸다. 오른쪽에 읽어 온 원문을 둔다.
 * 그룹에 올리기와 내 공고 넣기가 함께 쓰고, 다른 것은 글자와 보낸 뒤의 일뿐이다.
 */
export function PostCheckForm({ draft, backTo, backLabel, submitLabel, optionalNote, unknownNote, notice, onSubmit }: PostCheckFormProps) {
  const [company, setCompany] = useState(draft.company)
  const [title, setTitle] = useState(draft.title)
  const [dueMode, setDueMode] = useState<DueMode>(draft.deadline.kind === "unknown" && draft.unread.includes("deadline") ? "date" : draft.deadline.kind)
  const [dueDate, setDueDate] = useState(draft.deadline.kind === "date" ? draft.deadline.date : "")
  const [sourceUrl, setSourceUrl] = useState(draft.sourceUrl)
  const [applyAt, setApplyAt] = useState(draft.applyAt)
  const [sections, setSections] = useState(draft.sections.map((s) => ({ ...s, text: s.items.join("\n") })))
  const [errors, setErrors] = useState<Partial<Record<DraftField, string>>>({})
  const [saving, setSaving] = useState(false)

  /** 읽지 못했고 아직 비어 있는 칸 */
  const unread = (field: DraftField, value: string) => draft.unread.includes(field) && !value.trim()
  const dueUnread = dueMode === "date" && unread("deadline", dueDate)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const next: Partial<Record<DraftField, string>> = {}
    if (!company.trim()) next.company = "회사명을 넣어 주세요."
    if (!title.trim()) next.title = "공고명을 넣어 주세요."
    if (dueMode === "date" && !dueDate) next.deadline = "마감일을 넣거나 상시, 모름을 고르세요."
    if (sourceUrl.trim() && !isHttpUrl(sourceUrl.trim())) next.sourceUrl = "https:// 로 시작하는 주소를 넣거나 비워 두세요."
    setErrors(next)
    if (Object.keys(next).length) return
    setSaving(true)
    try {
      const deadline: Deadline = dueMode === "date" ? { kind: "date", date: dueDate } : { kind: dueMode }
      await onSubmit({
        company: company.trim(),
        title: title.trim(),
        deadline,
        sourceUrl: sourceUrl.trim() || null,
        applyAt: applyAt.trim() || null,
        sections: sections
          .map((s) => ({ title: s.title, items: s.text.split("\n").map((l) => l.trim()).filter(Boolean) }))
          .filter((s) => s.items.length > 0),
        keywords: draft.keywords,
      })
    } finally {
      setSaving(false)
    }
  }

  const field = (key: "company" | "title" | "sourceUrl", label: string, value: string, set: (v: string) => void, type = "text") => (
    <Field id={`pc-${key}`} label={label} evidence={draft.evidence[key]} error={errors[key]} unread={unread(key, value)} name={FIELD_NAMES[key]}>
      <Input
        id={`pc-${key}`}
        type={type}
        value={value}
        maxLength={MAX_LENGTH[key]}
        aria-invalid={errors[key] ? true : undefined}
        aria-describedby={noteId(`pc-${key}`)}
        onChange={(e) => {
          set(e.target.value)
          setErrors((er) => ({ ...er, [key]: undefined }))
        }}
        className={joinClasses("md:text-base aria-invalid:ring-0", unread(key, value) && unreadInput)}
      />
    </Field>
  )

  return (
    <Page className="gap-6 pt-10">
      <BackLink to={backTo}>{backLabel}</BackLink>
      <div className="flex flex-col gap-2">
        <h1 className={subpageTitle}>칸 확인</h1>
        <p className="text-text-secondary">읽어 온 내용이 맞는지 보고 고칩니다. 노란 칸은 읽지 못한 칸입니다.</p>
      </div>

      <div className={columns}>
        <form onSubmit={submit} noValidate className={joinClasses(mainColumn, "gap-6")}>
          {notice}

          <Panel aria-labelledby="pc-must" className="flex flex-col gap-4 p-6">
            <h2 id="pc-must" className={sectionTitle}>
              꼭 있어야 하는 칸
            </h2>
            {field("company", "회사명", company, setCompany)}
            {field("title", "공고명", title, setTitle)}
            <div className="flex flex-col gap-1.5">
              <span id="pc-due-label" className="text-sm font-medium">
                마감
              </span>
              <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
                <SegmentedControl
                  label="마감"
                  value={dueMode}
                  onValueChange={(value) => {
                    setDueMode(value as DueMode)
                    setErrors((er) => ({ ...er, deadline: undefined }))
                  }}
                  options={DUE_MODES}
                />
                {dueMode === "date" && (
                  <>
                    <label htmlFor="pc-due" className="sr-only">
                      마감일
                    </label>
                    <Input
                      id="pc-due"
                      type="date"
                      value={dueDate}
                      aria-invalid={errors.deadline ? true : undefined}
                      aria-describedby={noteId("pc-due")}
                      onChange={(e) => {
                        setDueDate(e.target.value)
                        setErrors((er) => ({ ...er, deadline: undefined }))
                      }}
                      className={joinClasses("max-w-[220px] flex-[1_1_180px] md:text-base aria-invalid:ring-0", dueUnread && unreadInput)}
                    />
                  </>
                )}
              </div>
              {errors.deadline ? (
                <FieldError id="pc-due">{errors.deadline}</FieldError>
              ) : (
                dueUnread && (
                  <IconNote id={noteId("pc-due")} tone="warning">
                    {`마감일을 읽지 못했습니다. 원문을 보고 넣어 주세요. ${unknownNote}`}
                  </IconNote>
                )
              )}
            </div>
            {field("sourceUrl", "원문 링크", sourceUrl, setSourceUrl, "url")}
          </Panel>

          <Panel aria-labelledby="pc-more" className="flex flex-col gap-4 p-6">
            <div className="flex flex-col gap-1">
              <h2 id="pc-more" className={sectionTitle}>
                더 있으면 좋은 칸
              </h2>
              <p className="text-sm text-text-secondary">{optionalNote}</p>
            </div>
            <Field id="pc-at" label="지원하는 곳" evidence={draft.evidence.applyAt}>
              <Input id="pc-at" value={applyAt} maxLength={MAX_LENGTH.applyAt} onChange={(e) => setApplyAt(e.target.value)} className="md:text-base" />
            </Field>
            {sections.map((section, i) => (
              <Field key={section.title} id={`pc-section-${i}`} label={section.title} evidence={section.from ? `원문의 "${section.from}" 부분` : undefined}>
                <Textarea
                  id={`pc-section-${i}`}
                  rows={3}
                  value={section.text}
                  onChange={(e) => setSections((ss) => ss.map((s, j) => (j === i ? { ...s, text: e.target.value } : s)))}
                  className="min-h-24"
                />
              </Field>
            ))}
            <div className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">키워드</span>
              {draft.keywords.length > 0 ? (
                <ul className="flex flex-wrap gap-1.5">
                  {draft.keywords.map((k) => (
                    <Chip key={k}>{k}</Chip>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-text-secondary">사전에 있는 키워드를 찾지 못했습니다.</p>
              )}
              <p className="text-label-sm text-text-secondary">키워드 사전의 대표 표기로 뽑았습니다.</p>
            </div>
          </Panel>

          <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
            <Link to={backTo} className={buttonVariants({ variant: "outline" })}>
              취소
            </Link>
            <Button type="submit" className="px-6" disabled={saving}>
              {submitLabel}
            </Button>
          </div>
        </form>

        <aside aria-labelledby="pc-src" className={joinClasses(sideColumn(320), "gap-3")}>
          <h2 id="pc-src" className={sectionTitle}>
            읽어 온 원문
          </h2>
          <div className="flex flex-col gap-3 rounded-panel bg-surface-muted p-5 text-sm">
            <p className="font-bold">{draft.source.title}</p>
            {sourceParagraphs(draft.source.text, draft.sections.map((s) => s.from)).map((p, i) => (
              <p key={i} className="whitespace-pre-line">
                {p.heading && (
                  <>
                    <strong className="font-bold text-accent-strong">{p.heading}</strong>
                    <br />
                  </>
                )}
                {p.text}
              </p>
            ))}
          </div>
          <p className="text-label-sm text-text-secondary">굵은 글자가 칸을 채운 근거입니다.</p>
        </aside>
      </div>
    </Page>
  )
}

/** 입력 칸 한 묶음. 아래 글은 오류, 읽지 못함, 근거 순으로 하나만 보인다. */
function Field({
  id,
  label,
  evidence,
  error,
  unread,
  name,
  children,
}: {
  id: string
  label: string
  evidence?: string
  error?: string
  unread?: boolean
  name?: string
  children: ReactNode
}) {
  const note = error ? (
    <FieldError id={id}>{error}</FieldError>
  ) : unread && name ? (
    <IconNote id={noteId(id)} tone="warning">
      {name}
      {objectParticle(name)} 읽지 못했습니다. 원문을 보고 넣어 주세요.
    </IconNote>
  ) : (
    evidence && <FieldHint id={id}>근거: {evidence}</FieldHint>
  )
  return (
    <FormField id={id} label={label} note={note}>
      {children}
    </FormField>
  )
}
