import type { FormEvent } from "react"
import { Link } from "react-router"

import { Button } from "@/components/ui/button"
import { Heading } from "@/shared/components/heading"
import { paths } from "@/shared/lib/paths"

import { AuthField, AuthFooter, AuthFrame, authFooterLink, OrGoogle } from "../components/auth-parts"

/**
 * 로그인(시안 3종의 "A · 로그인", 모바일 390px). 넓은 화면에서는 같은 폭의 단을 가운데 둔다.
 */
export function LoginPage() {
  // shortcut: 서버가 없어 로그인은 아무것도 부르지 않는다, 서버를 붙이면 POST /api/auth/login 을 부른다(docs/리드미_설계_로그인_경험카드.md 4.1)
  const submit = (event: FormEvent) => event.preventDefault()

  return (
    <AuthFrame>
      <Heading level={1} size="text-[28px]" className="flex flex-col items-start gap-1.5">
        <span>취업준비생을 위한</span>
        <span className="-ml-1 rounded-sm bg-highlight px-2.5 py-1 text-highlight-foreground">정보공유·스터디</span>
      </Heading>

      <form onSubmit={submit} className="flex flex-col gap-4">
        <AuthField id="login-email" label="이메일" type="email" autoComplete="email" placeholder="name@example.com" />
        <AuthField id="login-password" label="비밀번호" type="password" autoComplete="current-password" />
        <Link
          to={paths.passwordReset()}
          className="-mt-3 -mb-1 inline-flex min-h-control-md items-center self-end text-sm font-medium text-primary"
        >
          비밀번호 재설정
        </Link>
        <Button type="submit" className="w-full">
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
