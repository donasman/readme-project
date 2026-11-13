import { useState } from "react"
import { Link, useLocation } from "react-router"

import { Button } from "@/components/ui/button"
import { MailIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"

import { resendVerification } from "../api"
import { AuthFooter, AuthFrame, authFooterLink, AuthHeading, AuthNote, AuthStrong, AuthText, StatusIcon } from "../components/auth-parts"

/** 이메일 인증 · 메일 보냄(5차 "이메일 인증"). 가입한 이메일은 앞 화면이 주소 대신 이동 상태로 넘긴다. */
export function SignupSentPage() {
  const email = (useLocation().state as { email?: string } | null)?.email ?? ""
  const [resent, setResent] = useState(false)

  return (
    <AuthFrame>
      <div className="flex flex-col gap-6">
        <StatusIcon>
          <MailIcon />
        </StatusIcon>
        <AuthHeading title="메일을 확인해 주세요">
          <AuthText>
            {email && (
              <>
                <AuthStrong>{email}</AuthStrong>으로{" "}
              </>
            )}
            가입 확인 메일을 보냈습니다. 메일의 링크를 누르면 가입이 끝납니다.
          </AuthText>
          <AuthText>링크는 24시간 동안 쓸 수 있습니다.</AuthText>
        </AuthHeading>
        {email && (
          <div className="flex flex-col gap-2">
            <Button
              type="button"
              variant="outline"
              className="w-full"
              onClick={async () => {
                await resendVerification(email)
                setResent(true)
              }}
            >
              메일 다시 보내기
            </Button>
            {resent && <AuthNote tone="success">다시 보냈습니다. 안 보이면 스팸함도 확인해 주세요.</AuthNote>}
          </div>
        )}
      </div>
      <AuthFooter prompt="이메일을 잘못 넣었나요?">
        <Link to={paths.signup()} className={authFooterLink}>
          다시 가입하기
        </Link>
      </AuthFooter>
    </AuthFrame>
  )
}
