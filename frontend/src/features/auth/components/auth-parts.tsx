import type { ComponentProps, ReactNode } from "react"
import { Link } from "react-router"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Heading } from "@/shared/components/heading"
import { LogoMark } from "@/shared/components/logo-mark"
import { CircleAlertIcon, CircleCheckIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { cn, joinClasses } from "@/shared/lib/utils"

// 로그인 쪽 화면(로그인, 회원가입, 이메일 인증, 비밀번호 재설정)이 함께 쓰는 조각.
// 시안: 리드미 시안 3종의 "A · 로그인"과 5차 "로그인·가입". 데스크톱도 가운데 390px 한 줄이다.

/** 가운데 390px 한 줄과 맨 위 로고. 아래 줄(AuthFooter)은 맨 아래에 붙는다. */
export function AuthFrame({ children }: { children: ReactNode }) {
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
      {children}
    </main>
  )
}

/** 화면 제목(28px)과 그 아래 설명. 설명은 문단마다 하나씩 넘긴다. */
export function AuthHeading({ title, children }: { title: ReactNode; children?: ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
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
        <AuthNote id={noteId} tone="danger">
          {error}
        </AuthNote>
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

/** 아이콘이 붙은 짧은 알림 줄: 오류(빨강) 또는 끝남(초록). */
export function AuthNote({ id, tone, children }: { id?: string; tone: "danger" | "success"; children: ReactNode }) {
  const Icon = tone === "danger" ? CircleAlertIcon : CircleCheckIcon
  return (
    <p id={id} role={tone === "success" ? "status" : undefined} className="flex items-start gap-1.5 text-label-sm font-medium">
      <Icon aria-hidden="true" className={joinClasses("mt-px size-4 flex-none", tone === "danger" ? "text-danger" : "text-success")} />
      <span>{children}</span>
    </p>
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

/** "또는" 줄과 구글로 계속하기. 구글 마크는 시안처럼 자리만 둔다. */
export function OrGoogle() {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-3 text-xs text-text-secondary">
        <span aria-hidden="true" className="h-px flex-1 bg-border" />
        <span>또는</span>
        <span aria-hidden="true" className="h-px flex-1 bg-border" />
      </div>
      {/* 구글 로그인: 서버를 붙이면 /oauth2/authorization/google 로 보낸다 */}
      <Button type="button" variant="outline" className="w-full">
        <span aria-hidden="true" className="size-[18px] rounded-full border-[1.5px] border-dashed border-border-strong" />
        <span>구글로 계속하기</span>
      </Button>
    </div>
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
