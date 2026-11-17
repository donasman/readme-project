import type { ComponentProps, ReactNode } from "react"
import { Link } from "react-router"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { GOOGLE_BIRTH_GENDER_NOTICE } from "@/domain/session/consent"
import { Heading } from "@/shared/components/heading"
import { LogoMark } from "@/shared/components/logo-mark"
import { IconNote } from "@/shared/components/notice"
import { paths } from "@/shared/lib/paths"
import { cn, docTitle, joinClasses } from "@/shared/lib/utils"

import { startGoogleLogin } from "../api"

// 로그인 쪽 화면(로그인, 회원가입, 이메일 인증, 비밀번호 재설정)이 함께 쓰는 조각.
// 시안: 리드미 시안 3종의 "A · 로그인"과 5차 "로그인·가입". 데스크톱도 가운데 390px 한 줄이다.

/** 가운데 390px 한 줄과 맨 위 로고. 아래 줄(AuthFooter)은 맨 아래에 붙는다. */
export function AuthFrame({ children }: { children: ReactNode }) {
  return (
    <main id="main" className="mx-auto flex min-h-svh max-w-[390px] flex-col gap-8 px-6 pt-14 pb-6">
      <Link
        to={paths.home()}
        aria-label="리드미 홈"
        className="inline-flex min-h-control-md items-center gap-2.5 self-start text-[22px] leading-display font-bold tracking-display text-foreground"
      >
        <LogoMark size={40} />
        <span>리드미</span>
      </Link>
      {children}
    </main>
  )
}

/** 화면 제목(28px)과 그 아래 설명. 설명은 문단마다 하나씩 넘긴다. */
export function AuthHeading({ title, children }: { title: ReactNode; children?: ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      {typeof title === "string" && <title>{docTitle(title)}</title>}
      <Heading level={1} size="text-[28px]">
        {title}
      </Heading>
      {children}
    </div>
  )
}

/** 제목 아래 설명 한 문단. */
export function AuthText({ children }: { children: ReactNode }) {
  return <p className="text-sm text-text-secondary">{children}</p>
}

/** 설명 안에서 강조하는 값(이메일 주소). */
export function AuthStrong({ children }: { children: ReactNode }) {
  return <strong className="font-bold text-foreground">{children}</strong>
}

interface AuthFieldProps extends ComponentProps<typeof Input> {
  id: string
  label: string
  /** 입력 아래 작은 안내 글자 */
  hint?: string
  /** 있으면 빨간 테두리와 함께 안내 대신 보인다 */
  error?: ReactNode
}

/** 이름표, 입력, 안내(또는 오류) 한 묶음. */
export function AuthField({ id, label, hint, error, className, ...input }: AuthFieldProps) {
  const noteId = hint || error ? `${id}-note` : undefined
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <Input
        id={id}
        aria-describedby={noteId}
        aria-invalid={error ? true : undefined}
        className={cn("md:text-base aria-invalid:ring-0", className)}
        {...input}
      />
      {error ? (
        // 서버가 돌려준 오류(비밀번호 틀림 등)는 누른 버튼에 초점이 남아 alert로 알린다
        <div role="alert">
          <IconNote id={noteId} tone="danger">
            {error}
          </IconNote>
        </div>
      ) : (
        hint && (
          <p id={noteId} className="text-label-sm text-text-secondary">
            {hint}
          </p>
        )
      )}
    </div>
  )
}

/** 상태 화면 맨 위의 48px 아이콘 칸. accent: 메일 보냄·끝남, muted: 링크 만료. */
export function StatusIcon({ tone = "accent", children }: { tone?: "accent" | "muted"; children: ReactNode }) {
  return (
    <span
      aria-hidden="true"
      className={joinClasses(
        "inline-flex size-12 items-center justify-center rounded-control [&_svg]:size-6",
        tone === "accent" ? "bg-accent-subtle text-accent-strong" : "bg-surface-muted text-foreground"
      )}
    >
      {children}
    </span>
  )
}

/** "또는" 줄과 구글로 계속하기. 구글에서 생년월일·성별을 받는다는 안내를 함께 둔다(로그인 문서 7절 29번). */
export function OrGoogle() {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-3 text-xs text-text-secondary">
        <span aria-hidden="true" className="h-px flex-1 bg-border" />
        <span>또는</span>
        <span aria-hidden="true" className="h-px flex-1 bg-border" />
      </div>
      <Button type="button" variant="outline" className="w-full" onClick={startGoogleLogin}>
        <GoogleMark />
        <span>구글로 계속하기</span>
      </Button>
      <p className="text-label-sm text-text-secondary">{GOOGLE_BIRTH_GENDER_NOTICE}</p>
    </div>
  )
}

/** 구글 로그인 버튼의 G 마크. 구글의 로그인 버튼 안내(Sign in with Google branding)가 정한 네 색 그대로다. */
function GoogleMark() {
  return (
    <svg aria-hidden="true" viewBox="0 0 48 48" className="size-[18px] flex-none">
      <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
      <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
      <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
      <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
    </svg>
  )
}

/** 맨 아래 한 줄: 물음과 링크("계정이 없나요? 회원가입"). */
export function AuthFooter({ prompt, children }: { prompt: string; children: ReactNode }) {
  return (
    <p className="mt-auto flex items-center justify-center gap-1.5 text-sm text-text-secondary">
      <span>{prompt}</span>
      {children}
    </p>
  )
}

/** 맨 아래 줄의 링크. */
export const authFooterLink = "inline-flex min-h-control-md items-center font-bold text-primary"

/** 칸 아래 오류 글 안의 링크("로그인하거나 비밀번호를 재설정하세요"). */
export const authInlineLink = "font-bold text-primary hover:text-accent-strong"
