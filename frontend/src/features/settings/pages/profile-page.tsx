import {
  useState,
  type ComponentProps,
  type FormEvent,
  type ReactNode,
} from "react"
import { Link, useLoaderData, useSearchParams } from "react-router"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { getProfile } from "@/domain/profile/api"
import { formatCareerLength } from "@/domain/profile/format"
import { PROFILE_LIST_MAX } from "@/domain/profile/types"
import { ChoiceInput } from "@/shared/components/choice-input"
import {
  FieldError,
  FieldHint,
  FormField,
  noteId,
} from "@/shared/components/form-field"
import { Heading, sectionTitle } from "@/shared/components/heading"
import { IconNote, Notice } from "@/shared/components/notice"
import { Panel } from "@/shared/components/panel"
import { Pill } from "@/shared/components/pill"
import { textLink } from "@/shared/components/text-link"
import { PlusIcon } from "@/shared/icons"
import { seoulToday } from "@/shared/lib/date"
import { paths } from "@/shared/lib/paths"
import { docTitle, joinClasses } from "@/shared/lib/utils"

import { saveProfile } from "../api"
import { YearMonthField } from "../components/year-month-field"
import {
  certificateRenames,
  checkDraft,
  fieldId,
  isEmptyProfile,
  newCareer,
  newCertificate,
  newEducation,
  newLanguage,
  toDraft,
  type FieldErrors,
  type ProfileDraft,
} from "../profile-form"

export function profileLoader() {
  return getProfile()
}


// 자료에서 읽지 못해 비어 있는 칸(공고 칸 확인의 읽지 못한 칸과 같은 표시)
const missingInput =
  "border-2 border-highlight-strong bg-highlight-subtle dark:bg-highlight-subtle"

type Lists = Pick<
  ProfileDraft,
  "educations" | "certificates" | "languages" | "careers"
>

/** 이름표를 감싼 입력 하나. 오류가 있으면 빨갛게 하고 줄 아래 글과 잇는다. */
function TextField({
  id,
  label,
  error,
  className,
  ...props
}: { id: string; label: string; error?: string } & ComponentProps<"input">) {
  return (
    <label className={joinClasses("flex min-w-0 flex-col gap-1.5", className)}>
      <span className="text-sm font-medium">{label}</span>
      <Input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? noteId(id) : undefined}
        className="md:text-base"
        {...props}
      />
    </label>
  )
}

/** 학력·자격증·어학·경력의 한 줄: 테두리 상자, 지우기 버튼, 아래에 그 줄의 오류. */
function Entry({
  label,
  onRemove,
  errors,
  children,
}: {
  label: string
  onRemove: () => void
  errors: [string, string][]
  children: ReactNode
}) {
  return (
    <div
      role="group"
      aria-label={label}
      className="flex flex-col gap-3 rounded-control border border-border p-4"
    >
      <div className="flex flex-wrap items-end gap-3">
        {children}
        <Button
          type="button"
          variant="ghost"
          size="sm"
          aria-label={`${label} 지우기`}
          onClick={onRemove}
          className="min-h-11 text-text-secondary"
        >
          지우기
        </Button>
      </div>
      {errors.map(([id, text]) => (
        <IconNote key={id} id={noteId(id)} tone="danger">
          {text}
        </IconNote>
      ))}
    </div>
  )
}

/** 목록 구획: 제목, 줄들, 빈 글, 더하기 버튼(20줄까지), 맨 아래 안내. */
function ListSection({
  id,
  title,
  aside,
  note,
  count,
  empty,
  addLabel,
  onAdd,
  children,
}: {
  id: string
  title: string
  aside?: ReactNode
  note?: ReactNode
  count: number
  empty: string
  addLabel: string
  onAdd: () => void
  children: ReactNode
}) {
  return (
    <Panel aria-labelledby={id} className="flex flex-col gap-4 p-6">
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <h3 id={id} className={sectionTitle}>
          {title}
        </h3>
        {aside}
      </div>
      {children}
      {count === 0 && <p className="text-sm text-text-secondary">{empty}</p>}
      {count < PROFILE_LIST_MAX && (
        <Button
          type="button"
          variant="outline"
          onClick={onAdd}
          className="self-start"
        >
          <PlusIcon aria-hidden="true" />
          {addLabel}
        </Button>
      )}
      {note}
    </Panel>
  )
}

/** 기본 프로필(12차 시안, 프로필 설계 5절): 이력서에 적는 기본 정보. 모든 칸을 한 번에 저장한다(PUT은 전체를 갈아 끼운다). */
export function ProfilePage() {
  const loaded = useLoaderData<typeof profileLoader>()
  const [params] = useSearchParams()
  // 서버가 가진 값. 연차와 빈 프로필 안내는 저장한 값으로 보인다
  const [saved, setSaved] = useState(loaded)
  const [draft, setDraft] = useState(() => toDraft(loaded))
  // 카드 뽑기 결과의 "프로필 확인하기"로 왔을 때(?from=source). 저장하면 안내를 거둔다
  const [fromSource, setFromSource] = useState(params.get("from") === "source")
  const [errors, setErrors] = useState<FieldErrors>({})
  const [result, setResult] = useState<{
    kind: "saved" | "error"
    text: string
  } | null>(null)
  const [saving, setSaving] = useState(false)

  /** 칸 하나를 고친다. 그 칸의 오류와 저장 결과 글은 거둔다. */
  function change(id: string, next: (draft: ProfileDraft) => ProfileDraft) {
    setDraft(next)
    setResult(null)
    if (errors[id])
      setErrors((all) => {
        const rest = { ...all }
        delete rest[id]
        return rest
      })
  }
  function editRow<K extends keyof Lists>(
    list: K,
    key: number,
    id: string,
    patch: Partial<Lists[K][number]>
  ) {
    change(id, (d) => ({
      ...d,
      [list]: (d[list] as Lists[K][number][]).map((row) =>
        row.key === key ? { ...row, ...patch } : row
      ),
    }))
  }
  function removeRow(list: keyof Lists, key: number) {
    setDraft((d) => ({
      ...d,
      [list]: (d[list] as { key: number }[]).filter((row) => row.key !== key),
    }))
    setResult(null)
  }
  function addRow<K extends keyof Lists>(list: K, row: Lists[K][number]) {
    setDraft((d) => ({ ...d, [list]: [...d[list], row] }))
  }
  const rowErrors = (ids: string[]): [string, string][] =>
    ids.filter((id) => errors[id]).map((id) => [id, errors[id]])
  const missing = (value: string) => (fromSource && !value ? missingInput : "")

  async function save(event: FormEvent) {
    event.preventDefault()
    const checked = checkDraft(draft, seoulToday())
    if (checked.errors) {
      setErrors(checked.errors)
      const count = Object.keys(checked.errors).length
      return setResult({
        kind: "error",
        text: `저장하지 못했습니다. 표시한 칸 ${count}곳을 고쳐 주세요.`,
      })
    }
    setSaving(true)
    setResult(null)
    try {
      const response = await saveProfile(checked.input)
      if (!response.ok)
        return setResult({
          kind: "error",
          text: `저장하지 못했습니다. ${response.message}`,
        })
      const renames = certificateRenames(checked.input, response.profile)
      setSaved(response.profile)
      setDraft(toDraft(response.profile))
      setErrors({})
      setFromSource(false)
      setResult({
        kind: "saved",
        text: `저장했습니다.${renames.length ? ` 자격증 이름 ${renames.length === 1 ? "하나" : `${renames.length}개`}를 대표 표기로 바꿨습니다(${renames.join(", ")}).` : ""}`,
      })
    } finally {
      setSaving(false)
    }
  }

  const emptyList = fromSource
    ? "자료에서 읽지 못했습니다. 있으면 직접 넣어 주세요."
    : "아직 없습니다."

  return (
    <>
      <title>{docTitle("기본 프로필", "설정")}</title>
      <div className="flex flex-col gap-2">
        <Heading level={2} size="text-[22px]">
          기본 프로필
        </Heading>
        <p className="text-text-secondary">
          이력서에 적는 기본 정보입니다. 나만 보고 그룹에는 보이지 않습니다.
          전화번호와 주민번호는 받지 않습니다.
        </p>
      </div>

      {isEmptyProfile(saved) && !fromSource && (
        <Notice title="아직 비어 있습니다">
          <span>
            이력서나 포트폴리오를 올리면 읽을 수 있는 칸을 채웁니다. 직접 넣어도
            됩니다.
          </span>
          <Link
            to={paths.cardSources()}
            className={joinClasses(
              textLink,
              "-my-2 inline-flex min-h-11 items-center self-start no-underline"
            )}
          >
            자료 올리기
          </Link>
        </Notice>
      )}
      {fromSource && (
        <Notice title="자료에서 읽은 값을 넣었습니다">
          <span>
            맞는지 보고 고친 뒤 저장하세요. 노란 칸은 아직 비어 있는 칸입니다.
            이미 있던 값은 바꾸지 않았습니다.
          </span>
        </Notice>
      )}

      <form onSubmit={save} noValidate>
        {/* 저장하는 동안 고친 글이 응답으로 덮이지 않게 막는다 */}
        <fieldset disabled={saving} className="flex min-w-0 flex-col gap-6">
          <Panel
            aria-labelledby="pf-person"
            className="flex flex-col gap-4 p-6"
          >
            <h3 id="pf-person" className={sectionTitle}>
              인적사항
            </h3>
            <div className="flex flex-wrap gap-4">
              <FormField
                id="pf-name"
                label="이름"
                note={
                  <FieldHint id="pf-name">
                    이력서에 적는 이름입니다. 그룹에 보이는 표시 이름과
                    따로입니다.
                  </FieldHint>
                }
                className="min-w-0 flex-[2_1_240px]"
              >
                <Input
                  id="pf-name"
                  maxLength={50}
                  autoComplete="name"
                  value={draft.name}
                  onChange={(e) =>
                    change("pf-name", (d) => ({ ...d, name: e.target.value }))
                  }
                  aria-describedby={noteId("pf-name")}
                  className={joinClasses("md:text-base", missing(draft.name))}
                />
              </FormField>
              <FormField
                id="pf-birth"
                label={
                  <>
                    생년월일{" "}
                    <span className="font-normal text-text-secondary">
                      (선택)
                    </span>
                  </>
                }
                note={
                  errors["pf-birth"] && (
                    <FieldError id="pf-birth">{errors["pf-birth"]}</FieldError>
                  )
                }
                className="min-w-0 flex-[1_1_180px]"
              >
                <Input
                  id="pf-birth"
                  type="date"
                  min="1900-01-01"
                  max={seoulToday()}
                  value={draft.birthDate}
                  onChange={(e) =>
                    change("pf-birth", (d) => ({
                      ...d,
                      birthDate: e.target.value,
                    }))
                  }
                  aria-invalid={errors["pf-birth"] ? true : undefined}
                  aria-describedby={
                    errors["pf-birth"] ? noteId("pf-birth") : undefined
                  }
                  className={joinClasses(
                    "md:text-base",
                    missing(draft.birthDate)
                  )}
                />
              </FormField>
            </div>
          </Panel>

          <ListSection
            id="pf-edu"
            title="학력"
            count={draft.educations.length}
            empty={emptyList}
            addLabel="학력 더하기"
            onAdd={() => addRow("educations", newEducation())}
          >
            {draft.educations.map((e, i) => {
              const id = (field: string) => fieldId("edu", e.key, field)
              return (
                <Entry
                  key={e.key}
                  label={`학력 ${i + 1}`}
                  onRemove={() => removeRow("educations", e.key)}
                  errors={rowErrors([
                    id("school"),
                    id("graduation"),
                    id("gpa"),
                    id("scale"),
                  ])}
                >
                  <div className="flex w-full flex-wrap gap-3">
                    <TextField
                      id={id("school")}
                      label="학교"
                      maxLength={100}
                      value={e.school}
                      error={errors[id("school")]}
                      onChange={(v) =>
                        editRow("educations", e.key, id("school"), {
                          school: v.target.value,
                        })
                      }
                      className="flex-[1_1_200px]"
                    />
                    <TextField
                      id={id("major")}
                      label="전공"
                      maxLength={100}
                      value={e.major}
                      onChange={(v) =>
                        editRow("educations", e.key, id("major"), {
                          major: v.target.value,
                        })
                      }
                      className="flex-[1_1_200px]"
                    />
                  </div>
                  <YearMonthField
                    id={id("graduation")}
                    label="졸업(예정) 년월"
                    value={e.graduation}
                    invalid={!!errors[id("graduation")]}
                    describedBy={
                      errors[id("graduation")]
                        ? noteId(id("graduation"))
                        : undefined
                    }
                    onChange={(graduation) =>
                      editRow("educations", e.key, id("graduation"), {
                        graduation,
                      })
                    }
                    className="flex-[2_1_180px]"
                  />
                  <TextField
                    id={id("gpa")}
                    label="학점"
                    inputMode="decimal"
                    value={e.gpa}
                    error={errors[id("gpa")]}
                    onChange={(v) =>
                      editRow("educations", e.key, id("gpa"), {
                        gpa: v.target.value,
                      })
                    }
                    className="flex-[1_1_88px]"
                  />
                  <TextField
                    id={id("scale")}
                    label="만점"
                    inputMode="decimal"
                    value={e.gpaScale}
                    error={errors[id("scale")]}
                    onChange={(v) =>
                      editRow("educations", e.key, id("scale"), {
                        gpaScale: v.target.value,
                      })
                    }
                    className="flex-[1_1_88px]"
                  />
                </Entry>
              )
            })}
          </ListSection>

          <ListSection
            id="pf-cert"
            title="자격증"
            aside={
              <p className="w-full text-label-sm text-text-secondary">
                저장하면 이름을 대표 표기로 맞춥니다. 예: 컴활 → 컴퓨터활용능력
              </p>
            }
            count={draft.certificates.length}
            empty={emptyList}
            addLabel="자격증 더하기"
            onAdd={() => addRow("certificates", newCertificate())}
          >
            {draft.certificates.map((c, i) => {
              const id = (field: string) => fieldId("cert", c.key, field)
              return (
                <Entry
                  key={c.key}
                  label={`자격증 ${i + 1}`}
                  onRemove={() => removeRow("certificates", c.key)}
                  errors={rowErrors([id("name"), id("acquired")])}
                >
                  <TextField
                    id={id("name")}
                    label="이름"
                    maxLength={100}
                    value={c.name}
                    error={errors[id("name")]}
                    onChange={(v) =>
                      editRow("certificates", c.key, id("name"), {
                        name: v.target.value,
                      })
                    }
                    className="flex-[2_1_220px]"
                  />
                  <YearMonthField
                    id={id("acquired")}
                    label="취득 년월"
                    value={c.acquired}
                    invalid={!!errors[id("acquired")]}
                    describedBy={
                      errors[id("acquired")]
                        ? noteId(id("acquired"))
                        : undefined
                    }
                    onChange={(acquired) =>
                      editRow("certificates", c.key, id("acquired"), {
                        acquired,
                      })
                    }
                    className="flex-[1_1_200px]"
                  />
                </Entry>
              )
            })}
          </ListSection>

          <ListSection
            id="pf-lang"
            title="어학"
            count={draft.languages.length}
            empty={emptyList}
            addLabel="어학 더하기"
            onAdd={() => addRow("languages", newLanguage())}
          >
            {draft.languages.map((l, i) => {
              const id = (field: string) => fieldId("lang", l.key, field)
              return (
                <Entry
                  key={l.key}
                  label={`어학 ${i + 1}`}
                  onRemove={() => removeRow("languages", l.key)}
                  errors={rowErrors([id("test"), id("score"), id("taken")])}
                >
                  <TextField
                    id={id("test")}
                    label="시험"
                    maxLength={50}
                    value={l.test}
                    error={errors[id("test")]}
                    onChange={(v) =>
                      editRow("languages", l.key, id("test"), {
                        test: v.target.value,
                      })
                    }
                    className="flex-[2_1_160px]"
                  />
                  <TextField
                    id={id("score")}
                    label="점수"
                    maxLength={20}
                    value={l.score}
                    error={errors[id("score")]}
                    onChange={(v) =>
                      editRow("languages", l.key, id("score"), {
                        score: v.target.value,
                      })
                    }
                    className="flex-[1_1_100px]"
                  />
                  <YearMonthField
                    id={id("taken")}
                    label="응시 년월"
                    value={l.taken}
                    invalid={!!errors[id("taken")]}
                    describedBy={
                      errors[id("taken")] ? noteId(id("taken")) : undefined
                    }
                    onChange={(taken) =>
                      editRow("languages", l.key, id("taken"), { taken })
                    }
                    className="flex-[2_1_200px]"
                  />
                </Entry>
              )
            })}
          </ListSection>

          <ListSection
            id="pf-career"
            title="경력"
            aside={
              saved.careers.length > 0 && <Pill tone="accent">{formatCareerLength(saved)}</Pill>
            }
            note={
              <p className="text-label-sm text-text-secondary">
                연차는 저장할 때 다시 셉니다. 겹친 달은 한 번만 세고, 재직
                중이면 이번 달까지 셉니다.
              </p>
            }
            count={draft.careers.length}
            empty={emptyList}
            addLabel="경력 더하기"
            onAdd={() => addRow("careers", newCareer())}
          >
            {draft.careers.map((k, i) => {
              const id = (field: string) => fieldId("career", k.key, field)
              return (
                <Entry
                  key={k.key}
                  label={`경력 ${i + 1}`}
                  onRemove={() => removeRow("careers", k.key)}
                  errors={rowErrors([id("company"), id("start"), id("end")])}
                >
                  <div className="flex w-full flex-wrap gap-3">
                    <TextField
                      id={id("company")}
                      label="회사"
                      maxLength={100}
                      value={k.company}
                      error={errors[id("company")]}
                      onChange={(v) =>
                        editRow("careers", k.key, id("company"), {
                          company: v.target.value,
                        })
                      }
                      className="flex-[1_1_200px]"
                    />
                    <TextField
                      id={id("role")}
                      label="직무"
                      maxLength={100}
                      value={k.role}
                      onChange={(v) =>
                        editRow("careers", k.key, id("role"), {
                          role: v.target.value,
                        })
                      }
                      className="flex-[1_1_200px]"
                    />
                  </div>
                  <YearMonthField
                    id={id("start")}
                    label="시작 년월"
                    value={k.start}
                    invalid={!!errors[id("start")]}
                    describedBy={
                      errors[id("start")] ? noteId(id("start")) : undefined
                    }
                    onChange={(start) =>
                      editRow("careers", k.key, id("start"), { start })
                    }
                    className="flex-[1_1_200px]"
                  />
                  <YearMonthField
                    id={id("end")}
                    label="끝 년월"
                    value={k.end}
                    disabled={k.working}
                    invalid={!!errors[id("end")]}
                    describedBy={
                      errors[id("end")] ? noteId(id("end")) : undefined
                    }
                    onChange={(end) =>
                      editRow("careers", k.key, id("end"), { end })
                    }
                    className="flex-[1_1_200px]"
                  />
                  <label className="inline-flex min-h-11 flex-none items-center gap-2 text-sm">
                    <ChoiceInput
                      type="checkbox"
                      checked={k.working}
                      onChange={(v) =>
                        editRow("careers", k.key, id("end"), {
                          working: v.target.checked,
                        })
                      }
                    />
                    재직 중
                  </label>
                </Entry>
              )
            })}
          </ListSection>

          <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
            <div className="min-w-0 flex-[1_1_240px]">
              {result?.kind === "error" ? (
                <div role="alert">
                  <IconNote tone="danger">{result.text}</IconNote>
                </div>
              ) : result?.kind === "saved" ? (
                <IconNote tone="success">{result.text}</IconNote>
              ) : (
                <p className="text-label-sm text-text-secondary">
                  저장을 눌러야 바뀝니다. 모든 칸을 한 번에 저장합니다.
                </p>
              )}
            </div>
            <Button type="submit">저장</Button>
          </div>
        </fieldset>
      </form>
    </>
  )
}
