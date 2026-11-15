import { useState } from "react"
import { Link, useLoaderData, useNavigate, type LoaderFunctionArgs } from "react-router"

import { Button, buttonVariants } from "@/components/ui/button"
import { getViewer } from "@/domain/session/api"
import { rememberReturnTo } from "@/domain/session/return-to"
import { Avatar } from "@/shared/components/avatar"
import { ClockIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"

import { acceptInvite, getInvite } from "../api"
import { AuthFrame, AuthHeading, AuthText, StatusIcon } from "../components/auth-parts"

/** 초대 내용과 로그인했는지를 함께 읽는다. 로그인 전이어도 이 화면은 열린다. */
export async function inviteLoader({ params }: LoaderFunctionArgs) {
  const token = params.token ?? ""
  const [invite, viewer] = await Promise.all([getInvite(token), getViewer()])
  return { token, invite, signedIn: viewer !== null }
}

/** 그룹 초대 수락(시안 7차 "초대 수락"): 로그인한 사람 / 로그인 전 / 쓸 수 없는 링크. */
export function InvitePage() {
  const { token, invite, signedIn } = useLoaderData<typeof inviteLoader>()
  const navigate = useNavigate()
  const [expired, setExpired] = useState(invite.status === "expired")
  const [joining, setJoining] = useState(false)

  async function join() {
    setJoining(true)
    try {
      const result = await acceptInvite(token)
      if (result.ok) navigate(paths.groupPosts(result.groupId))
      else setExpired(true)
    } finally {
      setJoining(false)
    }
  }

  if (expired || invite.status === "expired") {
    return (
      <AuthFrame>
        <div className="flex flex-col gap-6">
          <StatusIcon tone="muted">
            <ClockIcon />
          </StatusIcon>
          <AuthHeading title="초대 링크를 쓸 수 없습니다">
            <AuthText>초대 링크는 7일 동안 쓸 수 있습니다. 그룹 멤버에게 새 링크를 부탁하세요.</AuthText>
          </AuthHeading>
          <Link to={paths.groups()} className={buttonVariants({ variant: "outline", className: "w-full" })}>
            내 그룹으로
          </Link>
        </div>
      </AuthFrame>
    )
  }

  return (
    <AuthFrame>
      <div className="flex flex-col gap-6">
        <Avatar size={56} tone="accent">
          {invite.mark}
        </Avatar>
        <AuthHeading title={`${invite.groupName}에 초대받았습니다`}>
          <AuthText>
            {invite.inviterName ? <>{invite.inviterName} 님이 만든 초대 링크입니다. 멤버 </> : "멤버 "}
            {invite.memberCount}명이 공고를 모으고 있습니다.
          </AuthText>
        </AuthHeading>
        <ul className="flex flex-col gap-3 rounded-control bg-surface-muted px-5 py-4 text-sm">
          <li>들어가면 멤버에게 내 이름이 보입니다.</li>
          <li>지원 상태는 내가 정한 공개 범위만큼만 보입니다. 처음에는 "지원 여부까지"입니다.</li>
          <li>언제든 나갈 수 있습니다.</li>
        </ul>
        {signedIn ? (
          <div className="flex flex-col gap-2">
            <Button type="button" className="w-full" disabled={joining} onClick={() => void join()}>
              그룹 들어가기
            </Button>
            <Link to={paths.groups()} className={buttonVariants({ variant: "outline", className: "w-full" })}>
              나중에
            </Link>
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            <Link to={paths.login()} onClick={() => rememberReturnTo(paths.invite(token))} className={buttonVariants({ className: "w-full" })}>
              로그인하고 들어가기
            </Link>
            <Link to={paths.signup()} onClick={() => rememberReturnTo(paths.invite(token))} className={buttonVariants({ variant: "outline", className: "w-full" })}>
              회원가입
            </Link>
            <p className="text-label-sm text-text-secondary">로그인하거나 가입을 마치면 이 화면으로 돌아옵니다.</p>
          </div>
        )}
      </div>
    </AuthFrame>
  )
}
