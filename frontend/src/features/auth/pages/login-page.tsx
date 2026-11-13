import type { FormEvent } from "react"
import { Link } from "react-router"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Heading } from "@/shared/components/heading"
import { LogoMark } from "@/shared/components/logo-mark"
import { PlaceholderLink } from "@/shared/components/placeholder-link"
import { paths } from "@/shared/lib/paths"

/**
 * 로그인(시안 3종의 "A · 로그인", 모바일 390px). 넓은 화면에서는 같은 폭의 단을 가운데 둔다.
 * 서버가 없어 로그인, 구글, 비밀번호 재설정, 회원가입은 아직 동작하지 않는다.
 */
export function LoginPage() {
  // 로그인: 서버를 붙이면 POST /api/auth/login 을 부른다(docs/리드미_설계_로그인_경험카드.md 4.1).
  const submit = (event: FormEvent) => event.preventDefault()

  return (
    <main className="mx-auto flex min-h-svh max-w-[390px] flex-col gap-8 px-6 pt-14 pb-6">
      <Link
        to={paths.home()}
        aria-label="리드미 홈"
        className="inline-flex min-h-control-md items-center gap-2.5 self-start text-[22px] leading-display font-bold tracking-display text-foreground"
      >
        <LogoMark size={40} />
        <span>리드미</span>
      </Link>

      <Heading level={1} size="text-[28px]" className="flex flex-col items-start gap-1.5">
        <span>취업준비생을 위한</span>
        <span className="-ml-1 rounded-sm bg-highlight px-2.5 py-1 text-highlight-foreground">정보공유·스터디</span>
      </Heading>

      <form onSubmit={submit} className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="login-email" className="text-sm font-medium">
            이메일
          </label>
          <Input id="login-email" type="email" autoComplete="email" placeholder="name@example.com" className="md:text-base" />
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="login-password" className="text-sm font-medium">
            비밀번호
          </label>
          <Input id="login-password" type="password" autoComplete="current-password" className="md:text-base" />
        </div>
        {/* 비밀번호 재설정: 아직 화면이 없다 */}
        <PlaceholderLink className="-mt-3 -mb-1 inline-flex min-h-control-md items-center self-end text-sm font-medium text-primary">
          비밀번호 재설정
        </PlaceholderLink>
        <Button type="submit" className="w-full">
          로그인
        </Button>
      </form>

      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-3 text-xs text-text-secondary">
          <span aria-hidden="true" className="h-px flex-1 bg-border" />
          <span>또는</span>
          <span aria-hidden="true" className="h-px flex-1 bg-border" />
        </div>
        {/* 구글 로그인: 서버를 붙이면 /oauth2/authorization/google 로 보낸다. 구글 마크는 시안처럼 자리만 둔다 */}
        <Button type="button" variant="outline" className="w-full">
          <span aria-hidden="true" className="size-[18px] rounded-full border-[1.5px] border-dashed border-border-strong" />
          <span>구글로 계속하기</span>
        </Button>
      </div>

      <p className="mt-auto flex items-center justify-center gap-1.5 text-sm text-text-secondary">
        <span>계정이 없나요?</span>
        {/* 회원가입: 아직 화면이 없다 */}
        <PlaceholderLink className="inline-flex min-h-control-md items-center font-bold text-primary">회원가입</PlaceholderLink>
      </p>
    </main>
  )
}
