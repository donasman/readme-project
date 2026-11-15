import { useNavigate } from "react-router"

import { PostReadStep } from "@/domain/posting/post-read-step"
import type { PostCheckState } from "@/domain/posting/types"
import { BackLink } from "@/shared/components/back-link"
import { subpageTitle } from "@/shared/components/heading"
import { paths } from "@/shared/lib/paths"

/** 내 공고 넣기(시안 10차): 그룹에 올리지 않고 내 공고에만 넣는다. 읽기는 공고 올리기와 같고, 읽으면 칸 확인으로 간다. */
export function MyPostNewPage() {
  const navigate = useNavigate()

  return (
    <main className="mx-auto flex max-w-[760px] flex-col gap-6 px-6 pt-10 pb-16">
      <BackLink to={paths.myPosts()}>내 공고</BackLink>
      <div className="flex flex-col gap-2">
        <h1 className={subpageTitle}>공고 넣기</h1>
        <p className="text-text-secondary">그룹에 올리지 않고 내 공고에만 넣습니다.</p>
      </div>

      <PostReadStep
        groupId={null}
        onDraft={(draft) => {
          const state: PostCheckState = { draft }
          void navigate(paths.myPostCheck(), { state })
        }}
      />

      <p className="text-label-sm text-text-secondary">넣은 공고는 그룹에 올라가지 않습니다. 지원 상태는 "미정"으로 시작하고 지원 현황에서 바꿉니다.</p>
    </main>
  )
}
