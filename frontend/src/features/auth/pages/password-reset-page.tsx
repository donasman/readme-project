import type { FormEvent } from "react"
import { Link, useNavigate } from "react-router"

import { Button } from "@/components/ui/button"
import { requestPasswordReset } from "@/domain/session/api"
import { paths } from "@/shared/lib/paths"

import { AuthField, AuthFooter, AuthFrame, authFooterLink, AuthHeading, AuthText } from "../components/auth-parts"

/** 비밀번호 재설정 · 요청(5차 "비밀번호 재설정"). 보내면 메일을 확인하라는 화면으로 간다. */
export function PasswordResetPage() {
  const navigate = useNavigate()

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const email = String(new FormData(event.currentTarget).get("email") ?? "").trim()
    await requestPasswordReset(email)
    navigate(paths.passwordResetSent(), { state: { email } })
  }

  return (
    <AuthFrame>
      <div className="flex flex-col gap-6">
        <AuthHeading title="비밀번호 재설정">
          <AuthText>가입한 이메일을 넣으면 재설정 링크를 보냅니다.</AuthText>
        </AuthHeading>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <AuthField id="reset-email" name="email" label="이메일" type="email" autoComplete="email" placeholder="name@example.com" required />
          <Button type="submit" className="w-full">
            재설정 메일 보내기
          </Button>
        </form>
      </div>
      <AuthFooter prompt="비밀번호가 기억났나요?">
        <Link to={paths.login()} className={authFooterLink}>
          로그인
        </Link>
      </AuthFooter>
    </AuthFrame>
  )
}
