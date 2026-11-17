import { useState } from "react"
import { useLoaderData, useLocation, useNavigate, useRevalidator, type LoaderFunctionArgs } from "react-router"

import { setMyDisclosure } from "@/domain/group/api"
import type { Group } from "@/domain/group/types"

import { useGroup } from "@/domain/group/use-group"
import { useViewer } from "@/domain/session/use-viewer"
import { BackLink } from "@/shared/components/back-link"
import { Page } from "@/shared/components/page"
import { columns, mainColumn, sideColumn } from "@/shared/components/page-layout"
import { paths } from "@/shared/lib/paths"
import { docTitle, joinClasses } from "@/shared/lib/utils"

import { addComment, deleteComment, editPost, getGroupPost, takeDownPost, updateComment } from "../api"
import { Comments } from "../components/comments"
import { DeadlineCard } from "../components/deadline-card"
import { InterestedMembers } from "../components/interested-members"
import { PostContent } from "../components/post-content"
import { CommentDeleteDialog, CommentEditDialog, DisclosureDialog, PostEditDialog, TakedownDialog } from "../components/post-dialogs"
import type { Comment, GroupPostDetail, ListReturnState } from "../types"
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
  const location = useLocation()
  const [editingPost, setEditingPost] = useState<GroupPostDetail | null>(null)
  const [takingDown, setTakingDown] = useState<GroupPostDetail | null>(null)
  const [editing, setEditing] = useState<Comment | null>(null)
  const [deleting, setDeleting] = useState<Comment | null>(null)
  const [commentNotice, setCommentNotice] = useState("")
  const [disclosureFor, setDisclosureFor] = useState<Group | null>(null)
  const interest = useInterest(group.id)
  const post = interest.apply(data.post)

  const isLeader = group.leaderId === viewer.id
  const isPoster = post.postedBy?.id === viewer.id
  const canTakeDown = isLeader || isPoster
  // 목록에서 왔으면 그때 고른 필터 그대로 돌아간다(브라우저 뒤로 가기와 같게)
  const listPath = paths.groupPosts(group.id) + ((location.state as ListReturnState | null)?.listSearch ?? "")

  return (
    <Page className="gap-4 pt-6">
      <title>{docTitle(post.title, group.name)}</title>
      <BackLink to={listPath}>공고 목록</BackLink>

      <div className={columns}>
        <div className={joinClasses(mainColumn, "gap-6")}>
          <DeadlineCard post={post} today={today} onToggleInterest={(on) => void interest.toggle(post.id, on)} />
          <PostContent post={post} viewer={viewer} canEdit={isPoster} onEdit={() => setEditingPost(post)} canTakeDown={canTakeDown} onTakeDown={() => setTakingDown(post)} />
        </div>
        <div className={joinClasses(sideColumn(340), "gap-6")}>
          <InterestedMembers post={post} viewer={viewer} myDisclosure={group.myDisclosure} onChangeDisclosure={() => setDisclosureFor(group)} />
          <Comments
            comments={post.comments}
            viewer={viewer}
            isLeader={isLeader}
            notice={commentNotice}
            onAdd={async (text) => {
              setCommentNotice("")
              await addComment(group.id, post.id, text)
              await revalidator.revalidate()
              setCommentNotice("댓글을 달았습니다.")
            }}
            onEdit={setEditing}
            onDelete={setDeleting}
          />
        </div>
      </div>

      <PostEditDialog
        target={editingPost}
        onClose={() => setEditingPost(null)}
        onSave={async (edit) => {
          const result = await editPost(group.id, post.id, edit)
          if (result.ok) {
            await revalidator.revalidate()
            setEditingPost(null)
          }
          return result
        }}
      />
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
          setCommentNotice("")
          if (editing) await updateComment(group.id, post.id, editing.id, text)
          await revalidator.revalidate()
          setEditing(null)
          setCommentNotice("댓글을 고쳤습니다.")
        }}
      />
      <CommentDeleteDialog
        target={deleting}
        onClose={() => setDeleting(null)}
        onConfirm={async () => {
          setCommentNotice("")
          if (deleting) await deleteComment(group.id, post.id, deleting.id)
          await revalidator.revalidate()
          setDeleting(null)
          setCommentNotice("댓글을 지웠습니다.")
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
