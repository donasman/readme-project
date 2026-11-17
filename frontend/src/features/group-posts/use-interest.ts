import { useCallback, useRef, useState } from "react"
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
  // 저장 중인 공고. 두 번 누르면 바뀐 버튼을 다시 눌러 켜기 다음 끄기가 나가고, 서버는 끌 때 내 공고에서 빼지 않아 공고만 남는다
  const saving = useRef(new Set<string>())

  const toggle = useCallback(
    async (postId: string, interested: boolean) => {
      if (saving.current.has(postId)) return
      saving.current.add(postId)
      setPending((p) => ({ ...p, [postId]: interested }))
      try {
        await setInterest(groupId, postId, interested)
        await revalidator.revalidate()
      } finally {
        saving.current.delete(postId)
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
