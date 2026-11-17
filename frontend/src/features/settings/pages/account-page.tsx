import { useEffect, useState, type FormEvent, type ReactNode } from "react"
import { useLoaderData, useNavigate, useRevalidator, useSearchParams } from "react-router"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { NAME_MAX_LENGTH } from "@/domain/session/types"
import { FieldError, FieldHint, FormField, noteId } from "@/shared/components/form-field"
import { sectionTitle } from "@/shared/components/heading"
import { IconNote } from "@/shared/components/notice"
import { Panel } from "@/shared/components/panel"
import { Pill } from "@/shared/components/pill"
import { paths } from "@/shared/lib/paths"
import { docTitle } from "@/shared/lib/utils"

import { getAccount, logout, renameMe, startGoogleLink } from "../api"
import { PasswordDialog } from "../components/password-dialog"

export function accountLoader() {
  return getAccount()
}

/** 한 줄: 왼쪽에 이름과 값, 오른쪽에 표시나 버튼. 위에 가는 줄. */
function Row({ label, value, children }: { label: string; value: ReactNode; children: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-t border-border-subtle py-4">
      <div className="flex flex-col gap-1">
        <span className="text-sm font-medium">{label}</span>
        {value}
      </div>
      {children}
    </div>
  )
}

/** 구글 연결에서 돌아온 결과(?google=)와 그 안내(로그인 문서 7절 23번). 모르는 값은 무시한다. */
const GOOGLE_RESULTS = new Map<string, { tone: "success" | "danger"; text: string }>([
  ["linked", { tone: "success", text: "구글 계정을 연결했습니다." }],
  ["in-use", { tone: "danger", text: "이 구글 계정은 다른 리드미 계정에 연결되어 있습니다." }],
  ["has-google", { tone: "danger", text: "이미 다른 구글 계정이 연결되어 있습니다." }],
  ["error", { tone: "danger", text: "구글 계정을 연결하지 못했습니다. 다시 시도해 주세요." }],
])

/** 내 정보(시안 9차): 표시 이름, 이메일, 로그인 방법(비밀번호, 구글), 로그아웃. */
export function AccountPage() {
  const account = useLoaderData<typeof accountLoader>()
  const navigate = useNavigate()
  const revalidator = useRevalidator()
  const [name, setName] = useState(account.name)
  const [note, setNote] = useState<{ kind: "error" | "saved"; text: string } | null>(null)
  // 비밀번호 창. 열 때마다 key를 바꿔 처음 단계로 연다
  const [passwordDialog, setPasswordDialog] = useState({ open: false, key: 0 })
  const [passwordChanged, setPasswordChanged] = useState(false)
  // 구글에서 돌아온 결과. 처음 값만 잡고 주소에서는 지운다(새로 고침이나 뒤로 가기에 같은 안내가 다시 뜨지 않게)
  const [params, setParams] = useSearchParams()
  const [googleResult] = useState(() => GOOGLE_RESULTS.get(params.get("google") ?? "") ?? null)
  useEffect(() => {
    if (params.has("google"))
      setParams(
        (next) => {
          next.delete("google")
          return next
        },
        { replace: true }
      )
  }, [params, setParams])

  async function save(event: FormEvent) {
    event.preventDefault()
    if (!name.trim()) return setNote({ kind: "error", text: "표시 이름을 넣어 주세요." })
    // 같은 "저장했습니다."도 다시 알리도록 먼저 비운다
    setNote(null)
    await renameMe(name.trim())
    // 헤더의 내 계정 동그라미(이름 첫 글자)도 새로 읽는다
    await revalidator.revalidate()
    setNote({ kind: "saved", text: "저장했습니다." })
  }

  return (
    <>
      <title>{docTitle("내 정보", "설정")}</title>
      <Panel aria-labelledby="ac-profile" className="flex flex-col gap-4 p-6">
        <h2 id="ac-profile" className={sectionTitle}>
          프로필
        </h2>
        <form onSubmit={save} noValidate>
          <FormField
            id="ac-name"
            label="표시 이름"
            note={note?.kind === "error" ? <FieldError id="ac-name">{note.text}</FieldError> : <FieldHint id="ac-name">{note?.kind === "saved" ? `${note.text} ` : ""}그룹 멤버에게 보이는 이름입니다.</FieldHint>}
          >
            <div className="flex flex-wrap gap-2">
              <Input
                id="ac-name"
                value={name}
                maxLength={NAME_MAX_LENGTH}
                aria-invalid={note?.kind === "error" || undefined}
                aria-describedby={noteId("ac-name")}
                onChange={(e) => {
                  setName(e.target.value)
                  setNote(null)
                }}
                className="w-auto min-w-0 flex-[1_1_200px] md:text-base aria-invalid:ring-0"
              />
              <Button type="submit" variant="outline">
                저장
              </Button>
            </div>
          </FormField>
          <p role="status" className="sr-only">
            {note?.kind === "saved" ? note.text : ""}
          </p>
        </form>
        <Row label="이메일" value={<span>{account.email}</span>}>
          {account.emailVerified && <Pill tone="accent">인증됨</Pill>}
        </Row>
      </Panel>

      <Panel aria-labelledby="ac-login" className="flex flex-col p-6">
        <h2 id="ac-login" className="mb-4 text-lg font-bold">
          로그인 방법
        </h2>
        <Row
          label="비밀번호"
          value={
            <>
              <span className="text-sm text-text-secondary">{account.hasPassword ? "설정되어 있습니다" : "설정되어 있지 않습니다"}</span>
              {passwordChanged && (
                <div className="mt-1">
                  <IconNote tone="success">비밀번호를 바꿨습니다. 다른 기기는 로그아웃되었습니다.</IconNote>
                </div>
              )}
            </>
          }
        >
          <Button type="button" variant="outline" size="sm" onClick={() => setPasswordDialog((d) => ({ open: true, key: d.key + 1 }))}>
            {account.hasPassword ? "비밀번호 바꾸기" : "비밀번호 만들기"}
          </Button>
        </Row>
        <PasswordDialog
          key={passwordDialog.key}
          open={passwordDialog.open}
          onClose={() => setPasswordDialog((d) => ({ ...d, open: false }))}
          email={account.email}
          hasPassword={account.hasPassword}
          onChanged={() => {
            setPasswordDialog((d) => ({ ...d, open: false }))
            setPasswordChanged(true)
          }}
        />
        {account.google ? (
          <Row label="구글" value={<span className="text-sm text-text-secondary">{account.google.email ?? "구글"} 계정이 연결되어 있습니다</span>}>
            <Pill tone="accent">연결됨</Pill>
          </Row>
        ) : (
          <Row
            label="구글"
            value={<span className="text-sm text-text-secondary">연결하면 구글로도 로그인할 수 있습니다</span>}
          >
            <Button type="button" variant="outline" size="sm" onClick={() => void startGoogleLink()}>
              구글 연결
            </Button>
          </Row>
        )}
        {googleResult && (
          <div className="pt-1">
            <IconNote tone={googleResult.tone}>{googleResult.text}</IconNote>
          </div>
        )}
        {/* 돌아온 페이지에는 안내가 처음부터 있어 화면 읽기가 읽지 않는다. 주소에서 google=을 지운 뒤에 채워 알린다 */}
        <p role="status" className="sr-only">
          {googleResult && !params.has("google") ? googleResult.text : ""}
        </p>
      </Panel>

      <Button
        type="button"
        variant="outline"
        className="self-start"
        onClick={async () => {
          await logout()
          navigate(paths.login())
        }}
      >
        로그아웃
      </Button>
    </>
  )
}
