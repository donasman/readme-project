import { Link, useLoaderData, useRevalidator, type LoaderFunctionArgs } from "react-router"

import { useGroup } from "@/domain/group/use-group"
import { useViewer } from "@/domain/session/use-viewer"
import { ArrowLeftIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"

import { addComment, getGroupPost } from "../api"
import { Comments } from "../components/comments"
import { DeadlineCard } from "../components/deadline-card"
import { InterestedMembers } from "../components/interested-members"
import { PostContent } from "../components/post-content"
import { useInterest } from "../use-interest"

export function groupPostLoader({ params }: LoaderFunctionArgs) {
  return getGroupPost(params.groupId ?? "", params.postId ?? "")
}

/** 그룹 공고 상세(docs/design/group-post.dc.html). */
export function GroupPostPage() {
  const data = useLoaderData<typeof groupPostLoader>()
  const { today } = data
  const viewer = useViewer()
  const { group } = useGroup()
  const revalidator = useRevalidator()
  const interest = useInterest(group.id)
  const post = interest.apply(data.post)

  const isLeader = group.leaderId === viewer.id
  const canTakeDown = isLeader || post.postedBy.id === viewer.id
  const listPath = paths.groupPosts(group.id)

  return (
    <main className="mx-auto flex max-w-[1240px] flex-col gap-4 px-4 pt-6 pb-16">
      <Link
        to={listPath}
        className="inline-flex min-h-control-md items-center gap-1.5 self-start text-sm font-bold text-text-secondary"
      >
        <ArrowLeftIcon aria-hidden="true" className="size-4" />
        <span>공고 목록</span>
      </Link>

      <div className="flex flex-wrap items-start gap-6">
        <div className="flex min-w-0 flex-[999_1_560px] flex-col gap-6">
          <DeadlineCard post={post} today={today} onToggleInterest={(on) => void interest.toggle(post.id, on)} />
          <PostContent post={post} viewer={viewer} canTakeDown={canTakeDown} />
        </div>
        <div className="flex min-w-0 flex-[1_1_340px] flex-col gap-6">
          <InterestedMembers post={post} viewer={viewer} myDisclosure={group.myDisclosure} />
          <Comments
            comments={post.comments}
            viewer={viewer}
            isLeader={isLeader}
            onAdd={async (text) => {
              await addComment(group.id, post.id, text)
              await revalidator.revalidate()
            }}
          />
        </div>
      </div>
    </main>
  )
}
