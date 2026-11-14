import { useState, type FormEvent, type ReactNode } from "react"
import { Link, useLoaderData, useNavigate, useRevalidator } from "react-router"

import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { FieldError, FieldHint, FormField, noteId } from "@/shared/components/form-field"
import { Panel } from "@/shared/components/panel"
import { Pill } from "@/shared/components/pill"
import { paths } from "@/shared/lib/paths"

import { getAccount, logout, renameMe } from "../api"

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

/** 내 정보(시안 9차): 표시 이름, 이메일, 로그인 방법(비밀번호, 구글), 로그아웃. */
export function AccountPage() {
  const account = useLoaderData<typeof accountLoader>()
  const navigate = useNavigate()
  const revalidator = useRevalidator()
  const [name, setName] = useState(account.name)
  const [note, setNote] = useState<{ kind: "error" | "saved"; text: string } | null>(null)

  async function save(event: FormEvent) {
    event.preventDefault()
    if (!name.trim()) return setNote({ kind: "error", text: "표시 이름을 넣어 주세요." })
    await renameMe(name.trim())
    // 헤더의 내 계정 동그라미(이름 첫 글자)도 새로 읽는다
    await revalidator.revalidate()
    setNote({ kind: "saved", text: "저장했습니다." })
  }

  return (
    <>
      <Panel aria-labelledby="ac-profile" className="flex flex-col gap-4 p-6">
        <h2 id="ac-profile" className="text-lg font-bold">
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
                maxLength={20}
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
        </form>
        <Row label="이메일" value={<span>{account.email}</span>}>
          {account.emailVerified && <Pill tone="accent">인증됨</Pill>}
        </Row>
      </Panel>

      <Panel aria-labelledby="ac-login" className="flex flex-col p-6">
        <h2 id="ac-login" className="mb-4 text-lg font-bold">
          로그인 방법
        </h2>
        <Row label="비밀번호" value={<span className="text-sm text-text-secondary">{account.hasPassword ? "설정되어 있습니다" : "설정되어 있지 않습니다"}</span>}>
          <Link to={paths.passwordReset()} className={buttonVariants({ variant: "outline", size: "sm" })}>
            비밀번호 바꾸기
          </Link>
        </Row>
        {account.google ? (
          <Row label="구글" value={<span className="text-sm text-text-secondary">{account.google.email} 계정이 연결되어 있습니다</span>}>
            <Pill tone="accent">연결됨</Pill>
          </Row>
        ) : (
          <Row label="구글" value={<span className="text-sm text-text-secondary">연결하면 구글로도 로그인할 수 있습니다</span>}>
            {/* shortcut: 구글 연결은 서버가 없어 눌러도 아무 일이 없다, 로그인 설계의 계정 연결(L3)이 정해지면 만든다(화면 API 대응 3.4의 17번) */}
            <Button type="button" variant="outline" size="sm">
              구글 연결
            </Button>
          </Row>
        )}
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
