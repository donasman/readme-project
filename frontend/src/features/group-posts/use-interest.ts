import { useCallback, useState } from "react"
import { useRevalidator } from "react-router"

import { setInterest } from "./api"
import type { GroupPost } from "./types"

/**
 * 관심 켜기·끄기. 누르면 바로 바뀐 것처럼 보이고(낙관적 갱신), 저장한 뒤 화면 데이터를 다시 읽는다.
 * apply()는 아직 저장 중인 값을 공고에 덮어 돌려준다.
 */
export function useInterest(groupId: string) {
  const revalidator = useRevalidator()
  const [pending, setPending] = useState<Record<string, boolean>>({})

  const toggle = useCallback(
    async (postId: string, interested: boolean) => {
      setPending((p) => ({ ...p, [postId]: interested }))
      try {
        await setInterest(groupId, postId, interested)
        await revalidator.revalidate()
      } finally {
        setPending((p) => {
          const next = { ...p }
          delete next[postId]
          return next
        })
      }
    },
    [groupId, revalidator]
  )

  const apply = useCallback(
    <T extends GroupPost>(post: T): T =>
      post.id in pending ? { ...post, me: { ...post.me, interested: pending[post.id] } } : post,
    [pending]
  )

  return { toggle, apply }
}
