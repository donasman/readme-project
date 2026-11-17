import { Link, useLocation } from "react-router"

import { buttonVariants } from "@/components/ui/button"
import { MailIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { StatusIcon } from "@/shared/components/status-icon"

import { AuthFrame, AuthHeading, AuthStrong, AuthText } from "../components/auth-parts"

/** 비밀번호 재설정 · 메일 보냄. 계정이 있는지 알려 주지 않으므로 "가입된 이메일이라면"이라고 쓴다. */
export function PasswordResetSentPage() {
  const email = (useLocation().state as { email?: string } | null)?.email ?? ""

  return (
    <AuthFrame>
      <div className="flex flex-col gap-6">
        <StatusIcon>
          <MailIcon />
        </StatusIcon>
        <AuthHeading title="메일을 확인해 주세요">
          <AuthText>
            가입된 이메일이라면{" "}
            {email && (
              <>
                <AuthStrong>{email}</AuthStrong>으로{" "}
              </>
            )}
            재설정 메일이 갑니다.
          </AuthText>
          <AuthText>링크는 1시간 동안 쓸 수 있습니다.</AuthText>
        </AuthHeading>
        <Link to={paths.login()} className={buttonVariants({ variant: "outline", className: "w-full" })}>
          로그인으로 돌아가기
        </Link>
      </div>
    </AuthFrame>
  )
}
