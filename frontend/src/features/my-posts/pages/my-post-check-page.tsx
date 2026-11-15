import { Navigate, useLocation, useNavigate } from "react-router"

import { PostCheckForm } from "@/domain/posting/post-check-form"
import type { PostCheckState } from "@/domain/posting/types"
import { paths } from "@/shared/lib/paths"

import { addMyPosting } from "../api"
import type { ArrivedState } from "../types"

/** 내 공고 넣기의 칸 확인(시안 10차): 읽어 온 칸을 고치고 내 공고에 넣는다. 넣으면 그 공고의 상세로 간다. */
export function MyPostCheckPage() {
  // shortcut: 읽은 결과는 서버에 두지 않아(공고 문서 4.6) 주소 상태로만 들고 온다. 새로 고치면 공고 넣기로 돌아간다, 남겨야 하면 sessionStorage에 둔다
  const draft = (useLocation().state as PostCheckState | null)?.draft
  const navigate = useNavigate()
  if (!draft) return <Navigate to={paths.myPostNew()} replace />

  return (
    <PostCheckForm
      narrow
      draft={draft}
      backTo={paths.myPostNew()}
      backLabel="공고 넣기"
      submitLabel="내 공고에 넣기"
      optionalNote="비워 두어도 넣을 수 있습니다."
      unknownNote={`"모름"으로 넣으면 내 공고에 "확인 필요"로 보입니다.`}
      onSubmit={async (upload) => {
        const result = await addMyPosting(upload)
        // 이미 내 공고에 있으면 그 공고로 가서 알린다(지원 상태와 메모는 그대로다). 뒤로 가기가 칸 확인으로 돌아와 다시 넣지 않게 바꿔 끼운다
        const state: ArrivedState | null = result.ok ? null : { arrived: true }
        await navigate(paths.myPost(result.postingId), { state, replace: true })
      }}
    />
  )
}
