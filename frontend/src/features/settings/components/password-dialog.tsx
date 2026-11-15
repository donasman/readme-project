import { useState, type FormEvent } from "react"
import { Link } from "react-router"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { PASSWORD_TOO_LONG, passwordTooLong } from "@/domain/session/password"
import { AppDialog, DialogCancel } from "@/shared/components/app-dialog"
import { FieldError, FieldHint, FormField, noteId } from "@/shared/components/form-field"
import { MailIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"

import { changePassword, sendPasswordMail } from "../api"

/** 창의 단계: 바꾸기(비밀번호가 있는 계정), 만들기(없는 계정), 재설정 메일을 보낸 뒤 */
type Step = "change" | "create" | "sent"

interface PasswordDialogProps {
  open: boolean
  onClose: () => void
  /** 계정 이메일. 제목 아래에 보이고, 만들기는 이 주소로 재설정 메일을 보낸다 */
  email: string
  hasPassword: boolean
  /** 바꾼 뒤. 창을 닫고 내 정보의 비밀번호 줄에 알린다 */
  onChanged: () => void
}

const note = "text-label-sm text-text-secondary"

/**
 * 비밀번호 바꾸기(시안 10차): 지금 비밀번호와 새 비밀번호(8자 이상). 비밀번호가 없는 계정(구글로만 가입)은
 * "비밀번호 만들기"로 열려 계정 이메일로 재설정 메일을 보낸다. 여는 쪽이 열 때마다 key를 바꿔 처음 단계로 연다.
 */
export function PasswordDialog({ open, onClose, email, hasPassword, onChanged }: PasswordDialogProps) {
  const [step, setStep] = useState<Step>(hasPassword ? "change" : "create")
  const [current, setCurrent] = useState("")
  const [next, setNext] = useState("")
  const [errors, setErrors] = useState<{ current?: string; next?: string }>({})
  const [busy, setBusy] = useState(false)

  async function change(event: FormEvent) {
    event.preventDefault()
    const found = {
      current: current ? undefined : "지금 비밀번호를 넣어 주세요.",
      next: next.length < 8 ? "8자 이상으로 넣어 주세요." : passwordTooLong(next) ? PASSWORD_TOO_LONG : undefined,
    }
    setErrors(found)
    if (found.current || found.next) return
    setBusy(true)
    try {
      const result = await changePassword(current, next)
      if (result === "done") return onChanged()
      // 다른 곳에서 비밀번호가 없어진 계정이면 만들기로 넘어간다
      if (result === "none") return setStep("create")
      setErrors({ current: "지금 비밀번호가 맞지 않습니다." })
    } finally {
      setBusy(false)
    }
  }

  async function sendMail() {
    setBusy(true)
    try {
      await sendPasswordMail(email)
      setStep("sent")
    } finally {
      setBusy(false)
    }
  }

  const close = (isOpen: boolean) => !isOpen && onClose()

  if (step === "sent") {
    return (
      <AppDialog
        open={open}
        onOpenChange={close}
        title="메일을 확인해 주세요"
        icon={
          <span aria-hidden="true" className="inline-flex size-12 items-center justify-center self-start rounded-control bg-accent-subtle text-accent-strong [&_svg]:size-6">
            <MailIcon />
          </span>
        }
        actions={<DialogCancel>닫기</DialogCancel>}
      >
        <p role="status" className="text-sm">
          재설정 메일을 <strong className="font-bold">{email}</strong> 주소로 보냈습니다. 링크는 1시간 동안 쓸 수 있습니다.
        </p>
        <p className={note}>메일은 1분에 한 번, 하루 다섯 번까지 보냅니다. 오지 않으면 잠시 뒤에 다시 받아 주세요.</p>
      </AppDialog>
    )
  }

  if (step === "create") {
    return (
      <AppDialog
        open={open}
        onOpenChange={close}
        title="비밀번호 만들기"
        subtitle={email}
        actions={
          <>
            <DialogCancel />
            <Button type="button" disabled={busy} onClick={() => void sendMail()}>
              재설정 메일 받기
            </Button>
          </>
        }
      >
        <p className="text-sm">구글로 로그인하는 계정이라 비밀번호가 없습니다. 재설정 메일의 링크에서 비밀번호를 만들면 이메일과 비밀번호로도 로그인할 수 있습니다.</p>
        <p className={note}>비밀번호를 만들면 모든 기기에서 로그아웃되어 다시 로그인해야 합니다.</p>
      </AppDialog>
    )
  }

  return (
    <AppDialog
      open={open}
      onOpenChange={close}
      title="비밀번호 바꾸기"
      subtitle={email}
      actions={
        <>
          <DialogCancel />
          <Button type="submit" form="password-form" disabled={busy}>
            비밀번호 바꾸기
          </Button>
        </>
      }
    >
      <form id="password-form" className="contents" onSubmit={change} noValidate>
        <FormField id="pw-current" label="지금 비밀번호" note={errors.current && <FieldError id="pw-current">{errors.current}</FieldError>}>
          <Input
            id="pw-current"
            type="password"
            autoComplete="current-password"
            value={current}
            aria-invalid={errors.current ? true : undefined}
            aria-describedby={errors.current ? noteId("pw-current") : undefined}
            onChange={(e) => {
              setCurrent(e.target.value)
              setErrors((er) => ({ ...er, current: undefined }))
            }}
            className="md:text-base aria-invalid:ring-0"
          />
        </FormField>
        <FormField
          id="pw-new"
          label="새 비밀번호"
          note={errors.next ? <FieldError id="pw-new">{errors.next}</FieldError> : <FieldHint id="pw-new">8자 이상</FieldHint>}
        >
          <Input
            id="pw-new"
            type="password"
            autoComplete="new-password"
            value={next}
            aria-invalid={errors.next ? true : undefined}
            aria-describedby={noteId("pw-new")}
            onChange={(e) => {
              setNext(e.target.value)
              setErrors((er) => ({ ...er, next: undefined }))
            }}
            className="md:text-base aria-invalid:ring-0"
          />
        </FormField>
        <p className={note}>바꾸면 이 기기는 로그인된 채로 있고, 다른 기기는 모두 로그아웃됩니다.</p>
        <p className={note}>
          지금 비밀번호가 기억나지 않으면{" "}
          <Link to={paths.passwordReset()} className="font-bold whitespace-nowrap text-primary">
            재설정 메일 받기
          </Link>
        </p>
      </form>
    </AppDialog>
  )
}
