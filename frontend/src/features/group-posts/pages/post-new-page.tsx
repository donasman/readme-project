import { Link, useNavigate } from "react-router"

import { useGroup } from "@/domain/group/use-group"
import { PostReadStep } from "@/domain/posting/post-read-step"
import type { PostCheckState } from "@/domain/posting/types"
import { BackLink } from "@/shared/components/back-link"
import { subpageTitle } from "@/shared/components/heading"
import { Notice } from "@/shared/components/notice"
import { paths } from "@/shared/lib/paths"

/** 공고 올리기(시안 7차): 링크, 본문 붙여넣기, 파일 가운데 하나로 읽고 칸 확인으로 간다. 이 그룹에 이미 있으면 그 공고를 알린다. */
export function PostNewPage() {
  const { group } = useGroup()
  const navigate = useNavigate()

  return (
    <main className="mx-auto flex max-w-[760px] flex-col gap-6 px-4 pt-6 pb-16">
      <BackLink to={paths.groupPosts(group.id)}>공고</BackLink>
      <div className="flex flex-col gap-1">
        <p className="text-sm text-text-secondary">{group.name}</p>
        <h1 className={subpageTitle}>공고 올리기</h1>
      </div>

      <PostReadStep
        groupId={group.id}
        onDraft={(draft) => {
          const state: PostCheckState = { draft }
          void navigate(paths.groupPostCheck(group.id), { state })
        }}
        duplicateNotice={(post) => (
          <Notice title="이미 이 그룹에 있는 공고입니다">
            <span>
              {post.company} · {post.title}. {post.postedBy ? <>{post.postedBy.name} 님이 </> : "탈퇴한 멤버가 "}
              {post.postedAt}에 올렸습니다.
            </span>
            <Link
              to={paths.groupPost(group.id, post.id)}
              className="-my-2 inline-flex min-h-control-md items-center self-start font-bold whitespace-nowrap text-primary"
            >
              그 공고 보기
            </Link>
          </Notice>
        )}
      />

      <p className="text-label-sm text-text-secondary">올리면 그룹 멤버 모두에게 보입니다. 그룹에서 내리는 것은 올린 사람과 그룹장만 할 수 있습니다.</p>
    </main>
  )
}
