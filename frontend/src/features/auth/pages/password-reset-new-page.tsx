import { useState, type FormEvent } from "react"
import { Link, useLoaderData, useNavigate, type LoaderFunctionArgs } from "react-router"

import { Button, buttonVariants } from "@/components/ui/button"
import { CheckIcon, ClockIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"

import { checkResetToken, resetPassword } from "../api"
import { AuthField, AuthFrame, AuthHeading, AuthText, StatusIcon } from "../components/auth-parts"

/** 메일의 재설정 링크(/password-reset/new?token=…)를 연 순간 아직 쓸 수 있는지 서버에 묻는다. */
export async function passwordResetNewLoader({ request }: LoaderFunctionArgs) {
  const token = new URL(request.url).searchParams.get("token")
  return { token, usable: await checkResetToken(token) }
}

/** 비밀번호 재설정 · 새 비밀번호 / 바꿈 / 링크 만료(5차 "비밀번호 재설정"). */
export function PasswordResetNewPage() {
  const { token, usable } = useLoaderData<typeof passwordResetNewLoader>()
  const navigate = useNavigate()
  const [step, setStep] = useState<"new" | "done" | "expired">(usable ? "new" : "expired")
  const [saving, setSaving] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    try {
      setStep(await resetPassword(token, String(new FormData(event.currentTarget).get("password") ?? "")))
    } finally {
      setSaving(false)
    }
  }

  if (step === "done") {
    return (
      <AuthFrame>
        <div className="flex flex-col gap-6">
          <StatusIcon>
            <CheckIcon />
          </StatusIcon>
          <AuthHeading title="비밀번호를 바꿨습니다">
            <AuthText>새 비밀번호로 로그인해 주세요.</AuthText>
          </AuthHeading>
          <Link to={paths.login()} className={buttonVariants({ className: "w-full" })}>
            로그인하기
          </Link>
        </div>
      </AuthFrame>
    )
  }

  if (step === "expired") {
    return (
      <AuthFrame>
        <div className="flex flex-col gap-6">
          <StatusIcon tone="muted">
            <ClockIcon />
          </StatusIcon>
          <AuthHeading title="링크를 쓸 수 없습니다">
            <AuthText>1시간이 지났거나 이미 쓴 링크입니다. 재설정 메일을 다시 받아 주세요.</AuthText>
          </AuthHeading>
          <Button type="button" className="w-full" onClick={() => navigate(paths.passwordReset())}>
            재설정 메일 다시 받기
          </Button>
        </div>
      </AuthFrame>
    )
  }

  return (
    <AuthFrame>
      <div className="flex flex-col gap-6">
        <AuthHeading title="새 비밀번호">
          <AuthText>바꾸면 다른 기기의 로그인이 모두 풀립니다.</AuthText>
        </AuthHeading>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <AuthField id="reset-password" name="password" label="새 비밀번호" type="password" autoComplete="new-password" hint="8자 이상" minLength={8} required />
          <Button type="submit" className="w-full" disabled={saving}>
            비밀번호 바꾸기
          </Button>
        </form>
      </div>
    </AuthFrame>
  )
}
