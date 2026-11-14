import { useState } from "react"
import { useLoaderData, useNavigate, useRevalidator, type LoaderFunctionArgs } from "react-router"

import { setMyDisclosure } from "@/domain/group/api"
import type { Group } from "@/domain/group/types"

import { useGroup } from "@/domain/group/use-group"
import { useViewer } from "@/domain/session/use-viewer"
import { BackLink } from "@/shared/components/back-link"
import { Page } from "@/shared/components/page"
import { columns, mainColumn, sideColumn } from "@/shared/components/page-layout"
import { paths } from "@/shared/lib/paths"
import { joinClasses } from "@/shared/lib/utils"

import { addComment, deleteComment, getGroupPost, takeDownPost, updateComment } from "../api"
import { Comments } from "../components/comments"
import { DeadlineCard } from "../components/deadline-card"
import { InterestedMembers } from "../components/interested-members"
import { PostContent } from "../components/post-content"
import { CommentDeleteDialog, CommentEditDialog, DisclosureDialog, TakedownDialog } from "../components/post-dialogs"
import type { Comment, GroupPostDetail } from "../types"
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
  const navigate = useNavigate()
  const [takingDown, setTakingDown] = useState<GroupPostDetail | null>(null)
  const [editing, setEditing] = useState<Comment | null>(null)
  const [deleting, setDeleting] = useState<Comment | null>(null)
  const [disclosureFor, setDisclosureFor] = useState<Group | null>(null)
  const interest = useInterest(group.id)
  const post = interest.apply(data.post)

  const isLeader = group.leaderId === viewer.id
  const canTakeDown = isLeader || post.postedBy.id === viewer.id
  const listPath = paths.groupPosts(group.id)

  return (
    <Page className="gap-4 pt-6">
      <BackLink to={listPath}>공고 목록</BackLink>

      <div className={columns}>
        <div className={joinClasses(mainColumn, "gap-6")}>
          <DeadlineCard post={post} today={today} onToggleInterest={(on) => void interest.toggle(post.id, on)} />
          <PostContent post={post} viewer={viewer} canTakeDown={canTakeDown} onTakeDown={() => setTakingDown(post)} />
        </div>
        <div className={joinClasses(sideColumn(340), "gap-6")}>
          <InterestedMembers post={post} viewer={viewer} myDisclosure={group.myDisclosure} onChangeDisclosure={() => setDisclosureFor(group)} />
          <Comments
            comments={post.comments}
            viewer={viewer}
            isLeader={isLeader}
            onAdd={async (text) => {
              await addComment(group.id, post.id, text)
              await revalidator.revalidate()
            }}
            onEdit={setEditing}
            onDelete={setDeleting}
          />
        </div>
      </div>

      <TakedownDialog
        target={takingDown}
        onClose={() => setTakingDown(null)}
        onConfirm={async () => {
          await takeDownPost(group.id, post.id)
          // 같은 그룹 안의 이동이라 그룹 loader(머리의 "공고 N")가 다시 돌지 않는다. 먼저 다시 읽으면 이 공고가 없어 오류가 나므로 옮긴 뒤 읽는다
          await navigate(listPath)
          await revalidator.revalidate()
        }}
      />
      <CommentEditDialog
        target={editing}
        onClose={() => setEditing(null)}
        onSave={async (text) => {
          if (editing) await updateComment(group.id, post.id, editing.id, text)
          await revalidator.revalidate()
          setEditing(null)
        }}
      />
      <CommentDeleteDialog
        target={deleting}
        onClose={() => setDeleting(null)}
        onConfirm={async () => {
          if (deleting) await deleteComment(group.id, post.id, deleting.id)
          await revalidator.revalidate()
          setDeleting(null)
        }}
      />
      <DisclosureDialog
        target={disclosureFor}
        onClose={() => setDisclosureFor(null)}
        onSave={async (disclosure) => {
          await setMyDisclosure(group.id, disclosure)
          await revalidator.revalidate()
          setDisclosureFor(null)
        }}
      />
    </Page>
  )
}
