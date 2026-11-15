import { useState } from "react"
import { Link, Navigate, useLocation, useNavigate, useRevalidator } from "react-router"

import { useGroup } from "@/domain/group/use-group"
import { PostCheckForm } from "@/domain/posting/post-check-form"
import type { PostCheckState, PostDraft } from "@/domain/posting/types"
import { Notice } from "@/shared/components/notice"
import { paths } from "@/shared/lib/paths"

import { uploadPost } from "../api"

/** 칸 확인(시안 7차): 읽어 온 칸을 고치고 그룹에 올린다. 같은 공고가 이 그룹에 있으면 그 공고를 알린다. */
export function PostCheckPage() {
  // shortcut: 읽은 결과는 서버에 두지 않아(공고 문서 4.6) 주소 상태로만 들고 온다. 새로 고치면 공고 올리기로 돌아간다, 남겨야 하면 sessionStorage에 둔다
  const draft = (useLocation().state as PostCheckState | null)?.draft
  const { group } = useGroup()
  if (!draft) return <Navigate to={paths.groupPostNew(group.id)} replace />
  return <GroupPostCheck draft={draft} />
}

function GroupPostCheck({ draft }: { draft: PostDraft }) {
  const { group } = useGroup()
  const navigate = useNavigate()
  const revalidator = useRevalidator()
  const [already, setAlready] = useState<string | null>(null)

  return (
    <PostCheckForm
      draft={draft}
      backTo={paths.groupPostNew(group.id)}
      backLabel="공고 올리기"
      submitLabel="그룹에 올리기"
      optionalNote="비워 두어도 올릴 수 있습니다."
      unknownNote={`"모름"으로 올리면 목록 맨 위 "마감일 확인 필요"에 들어갑니다.`}
      notice={
        already && (
          <Notice title="이미 이 그룹에 있는 공고입니다">
            <Link to={paths.groupPost(group.id, already)} className="-my-2 inline-flex min-h-control-md items-center self-start font-bold text-primary">
              그 공고 보기
            </Link>
          </Notice>
        )
      }
      onSubmit={async (upload) => {
        const result = await uploadPost(group.id, upload)
        if (!result.ok) return setAlready(result.postId)
        // 같은 그룹 안의 이동이라 그룹 loader(머리의 "공고 N")가 다시 돌지 않아 옮긴 뒤 다시 읽는다
        await navigate(paths.groupPosts(group.id))
        await revalidator.revalidate()
      }}
    />
  )
}
