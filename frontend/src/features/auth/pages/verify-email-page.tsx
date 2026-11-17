import type { FormEvent } from "react"
import { Link, useLoaderData, useNavigate, type LoaderFunctionArgs } from "react-router"

import { Button, buttonVariants } from "@/components/ui/button"
import { CheckIcon, ClockIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { StatusIcon } from "@/shared/components/status-icon"

import { resendVerification, verifyEmail } from "../api"
import { AuthField, AuthFooter, AuthFrame, authFooterLink, AuthHeading, AuthText } from "../components/auth-parts"

/** 메일의 가입 확인 링크(/verify-email?token=…)를 연 순간 서버에 확인한다. */
export async function verifyEmailLoader({ request }: LoaderFunctionArgs) {
  return { status: await verifyEmail(new URL(request.url).searchParams.get("token")) }
}

/** 이메일 인증 · 끝남 / 링크 만료(5차 "이메일 인증"). */
export function VerifyEmailPage() {
  const { status } = useLoaderData<typeof verifyEmailLoader>()
  const navigate = useNavigate()

  async function resend(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const email = String(new FormData(event.currentTarget).get("email") ?? "").trim()
    await resendVerification(email)
    navigate(paths.signupSent(), { state: { email } })
  }

  if (status === "done") {
    return (
      <AuthFrame>
        <div className="flex flex-col gap-6">
          <StatusIcon>
            <CheckIcon />
          </StatusIcon>
          <AuthHeading title="이메일 인증이 끝났습니다">
            <AuthText>이제 로그인할 수 있습니다.</AuthText>
          </AuthHeading>
          <Link to={paths.login()} className={buttonVariants({ className: "w-full" })}>
            로그인하기
          </Link>
        </div>
      </AuthFrame>
    )
  }

  return (
    <AuthFrame>
      <div className="flex flex-col gap-6">
        <StatusIcon tone="muted">
          <ClockIcon />
        </StatusIcon>
        <AuthHeading title="링크를 쓸 수 없습니다">
          <AuthText>24시간이 지났거나 이미 쓴 링크입니다. 가입한 이메일을 넣으면 확인 메일을 다시 보냅니다.</AuthText>
        </AuthHeading>
        <form onSubmit={resend} className="flex flex-col gap-4">
          <AuthField id="verify-email" name="email" label="이메일" type="email" autoComplete="email" placeholder="name@example.com" required />
          <Button type="submit" className="w-full">
            확인 메일 다시 받기
          </Button>
        </form>
      </div>
      <AuthFooter prompt="이미 인증했나요?">
        <Link to={paths.login()} className={authFooterLink}>
          로그인
        </Link>
      </AuthFooter>
    </AuthFrame>
  )
}
