import { useState, type FormEvent } from "react"
import { Link, useNavigate, useSearchParams } from "react-router"

import { Button } from "@/components/ui/button"
import { Heading } from "@/shared/components/heading"
import { IconNote } from "@/shared/components/notice"
import { sentence } from "@/shared/lib/korean"
import { paths } from "@/shared/lib/paths"
import { docTitle } from "@/shared/lib/utils"

import { login, type LoginResult } from "../api"
import { AuthField, AuthFooter, AuthFrame, authFooterLink, authInlineLink, OrGoogle } from "../components/auth-parts"

/**
 * 로그인(시안 3종의 "A · 로그인", 모바일 390px). 넓은 화면에서는 같은 폭의 단을 가운데 둔다.
 * 막히면(틀림, 인증 전, 시도가 너무 많음) 서버의 글을 비밀번호 칸 아래에 알린다. 구글 로그인이 실패하면 서버가 ?error=google로 보낸다.
 */
export function LoginPage() {
  const navigate = useNavigate()
  const googleFailed = useSearchParams()[0].get("error") === "google"
  const [failed, setFailed] = useState<Extract<LoginResult, { ok: false }> | null>(null)
  const [email, setEmail] = useState("")
  const [saving, setSaving] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    try {
      const result = await login(email.trim(), String(new FormData(event.currentTarget).get("password") ?? ""))
      if (result.ok) navigate(paths.home())
      else setFailed(result)
    } finally {
      setSaving(false)
    }
  }

  return (
    <AuthFrame>
      <title>{docTitle("로그인")}</title>
      <Heading level={1} size="text-[28px]" className="flex flex-col items-start gap-1.5">
        <span>취업준비생을 위한</span>
        <span className="-ml-1 rounded-sm bg-highlight px-2.5 py-1 text-highlight-foreground">정보공유·스터디</span>
      </Heading>

      <form onSubmit={submit} onChange={() => setFailed(null)} className="flex flex-col gap-4">
        {googleFailed && <IconNote tone="danger">구글로 로그인하지 못했습니다. 다시 시도해 주세요.</IconNote>}
        <AuthField
          id="login-email"
          name="email"
          label="이메일"
          type="email"
          autoComplete="email"
          placeholder="name@example.com"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
        <AuthField
          id="login-password"
          name="password"
          label="비밀번호"
          type="password"
          autoComplete="current-password"
          required
          error={
            failed && (
              <>
                {sentence(failed.message)}
                {failed.code === "EMAIL_NOT_VERIFIED" && (
                  <>
                    {" "}
                    <Link to={paths.signupSent()} state={{ email: email.trim() }} className={authInlineLink}>
                      확인 메일 다시 받기
                    </Link>
                  </>
                )}
              </>
            )
          }
        />
        <Link
          to={paths.passwordReset()}
          className="-mt-3 -mb-1 inline-flex min-h-control-md items-center self-end text-sm font-medium text-primary"
        >
          비밀번호 재설정
        </Link>
        <Button type="submit" className="w-full" disabled={saving}>
          로그인
        </Button>
      </form>

      <OrGoogle />

      <AuthFooter prompt="계정이 없나요?">
        <Link to={paths.signup()} className={authFooterLink}>
          회원가입
        </Link>
      </AuthFooter>
    </AuthFrame>
  )
}
