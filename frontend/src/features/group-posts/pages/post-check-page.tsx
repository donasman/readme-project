import { useState, type FormEvent, type ReactNode } from "react"
import { Link, Navigate, useLocation, useNavigate } from "react-router"

import SegmentedControl from "@/components/arc/segmented-control/segmented-control"
import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { useGroup } from "@/domain/group/use-group"
import { BackLink } from "@/shared/components/back-link"
import { Chip } from "@/shared/components/chip"
import { FieldError, FieldHint, FormField, noteId } from "@/shared/components/form-field"
import { sectionTitle, subpageTitle } from "@/shared/components/heading"
import { Page } from "@/shared/components/page"
import { columns, mainColumn, sideColumn } from "@/shared/components/page-layout"
import { IconNote, Notice } from "@/shared/components/notice"
import { Panel } from "@/shared/components/panel"
import { objectParticle } from "@/shared/lib/korean"
import { paths } from "@/shared/lib/paths"
import { isHttpUrl, joinClasses } from "@/shared/lib/utils"

import { uploadPost } from "../api"
import type { Deadline, DraftField, PostDraft } from "../types"

/** 공고 올리기가 읽은 결과를 들고 이 화면으로 온다. 새로 고쳐 결과가 없으면 공고 올리기로 돌아간다. */
export interface PostCheckState {
  draft: PostDraft
}

type DueMode = Deadline["kind"]
const DUE_MODES: { value: DueMode; label: string }[] = [
  { value: "date", label: "날짜" },
  { value: "always", label: "상시" },
  { value: "unknown", label: "모름" },
]
const FIELD_NAMES: Record<DraftField, string> = { company: "회사명", title: "공고명", deadline: "마감일", sourceUrl: "원문 링크", applyAt: "지원하는 곳" }

/** 읽지 못한 칸: 하이라이트 테두리와 옅은 바탕. */
const unreadInput = "border-2 border-highlight-strong bg-highlight-subtle dark:bg-highlight-subtle"

/** 칸 확인(시안 7차): 읽어 온 칸을 고치고 그룹에 올린다. 오른쪽에 읽어 온 원문을 둔다. */
export function PostCheckPage() {
  // shortcut: 읽은 결과를 주소 상태로만 들고 와서 새로 고치면 공고 올리기로 돌아간다, 결과를 어디에 둘지 정하면 바꾼다(화면 API 대응 3.2의 7번)
  const draft = (useLocation().state as PostCheckState | null)?.draft
  const { group } = useGroup()
  if (!draft) return <Navigate to={paths.groupPostNew(group.id)} replace />
  return <PostCheckForm draft={draft} />
}

function PostCheckForm({ draft }: { draft: PostDraft }) {
  const { group } = useGroup()
  const navigate = useNavigate()
  const [company, setCompany] = useState(draft.company)
  const [title, setTitle] = useState(draft.title)
  const [dueMode, setDueMode] = useState<DueMode>(draft.deadline.kind === "unknown" && draft.unread.includes("deadline") ? "date" : draft.deadline.kind)
  const [dueDate, setDueDate] = useState(draft.deadline.kind === "date" ? draft.deadline.date : "")
  const [sourceUrl, setSourceUrl] = useState(draft.sourceUrl)
  const [applyAt, setApplyAt] = useState(draft.applyAt)
  const [sections, setSections] = useState(draft.sections.map((s) => ({ ...s, text: s.items.join("\n") })))
  const [errors, setErrors] = useState<Partial<Record<DraftField, string>>>({})
  const [already, setAlready] = useState<string | null>(null)
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
      const result = await uploadPost(group.id, {
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
      if (result.ok) navigate(paths.groupPosts(group.id))
      else setAlready(result.postId)
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
      <BackLink to={paths.groupPostNew(group.id)}>공고 올리기</BackLink>
      <div className="flex flex-col gap-2">
        <h1 className={subpageTitle}>칸 확인</h1>
        <p className="text-text-secondary">읽어 온 내용이 맞는지 보고 고칩니다. 노란 칸은 읽지 못한 칸입니다.</p>
      </div>

      <div className={columns}>
        <form onSubmit={submit} noValidate className={joinClasses(mainColumn, "gap-6")}>
          {already && (
            <Notice title="이미 이 그룹에 있는 공고입니다">
              <Link to={paths.groupPost(group.id, already)} className="-my-2 inline-flex min-h-control-md items-center self-start font-bold text-primary">
                그 공고 보기
              </Link>
            </Notice>
          )}

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
                    마감일을 읽지 못했습니다. 원문을 보고 넣어 주세요. "모름"으로 올리면 목록 맨 위 "마감일 확인 필요"에 들어갑니다.
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
              <p className="text-sm text-text-secondary">비워 두어도 올릴 수 있습니다.</p>
            </div>
            <Field id="pc-at" label="지원하는 곳" evidence={draft.evidence.applyAt}>
              <Input id="pc-at" value={applyAt} onChange={(e) => setApplyAt(e.target.value)} className="md:text-base" />
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
            <Link to={paths.groupPostNew(group.id)} className={buttonVariants({ variant: "outline" })}>
              취소
            </Link>
            <Button type="submit" className="px-6" disabled={saving}>
              그룹에 올리기
            </Button>
          </div>
        </form>

        <aside aria-labelledby="pc-src" className={joinClasses(sideColumn(320), "gap-3")}>
          <h2 id="pc-src" className={sectionTitle}>
            읽어 온 원문
          </h2>
          <div className="flex flex-col gap-3 rounded-panel bg-surface-muted p-5 text-sm">
            <p className="font-bold">{draft.source.title}</p>
            {draft.source.paragraphs.map((p, i) => (
              <p key={i}>
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
          <p className="text-label-sm text-text-secondary">굵은 글자가 칸을 채운 근거입니다. {draft.source.note}</p>
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
