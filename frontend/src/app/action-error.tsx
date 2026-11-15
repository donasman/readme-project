import { useEffect, useState } from "react"
import { useLocation, useNavigate } from "react-router"

import { IconNote } from "@/shared/components/notice"
import { XIcon } from "@/shared/icons"
import { ApiError, errorMessage } from "@/shared/lib/api"
import { paths } from "@/shared/lib/paths"

/**
 * 버튼을 눌러 난 오류 가운데 화면이 따로 다루지 않은 것(처리되지 않은 Promise 거부)을 화면 아래에 알린다.
 * 칸 아래에 알릴 오류는 각 api.ts가 결과 값으로 돌려주므로 여기까지 오지 않는다. loader의 오류는 오류 화면이 보인다.
 * 로그인이 풀렸으면(401) 로그인 화면으로 보낸다. 알림은 닫거나 다른 화면으로 옮기면 사라진다.
 */
export function ActionErrorAlert() {
  const navigate = useNavigate()
  const { key: locationKey } = useLocation()
  // 같은 글이 이어서 나도 다시 알리도록 횟수를 key로 쓴다
  const [shown, setShown] = useState<{ text: string; count: number; at: string } | null>(null)

  useEffect(() => {
    const onRejection = (event: PromiseRejectionEvent) => {
      // 서버·연결의 오류는 여기서 다룬 것으로 친다. 그 밖(화면의 버그)은 콘솔에도 그대로 남긴다
      if (event.reason instanceof ApiError) event.preventDefault()
      if (event.reason instanceof ApiError && event.reason.status === 401) return void navigate(paths.login())
      setShown((s) => ({ text: errorMessage(event.reason), count: (s?.count ?? 0) + 1, at: locationKey }))
    }
    window.addEventListener("unhandledrejection", onRejection)
    return () => window.removeEventListener("unhandledrejection", onRejection)
  }, [navigate, locationKey])

  if (!shown || shown.at !== locationKey) return null
  return (
    <div
      key={shown.count}
      role="alert"
      className="fixed inset-x-4 bottom-6 z-50 mx-auto flex max-w-md items-start justify-between gap-3 rounded-control border border-border bg-surface-raised py-3 pr-2 pl-4 shadow-raised"
    >
      <IconNote tone="danger">{shown.text}</IconNote>
      <button
        type="button"
        aria-label="알림 닫기"
        onClick={() => setShown(null)}
        className="-my-1 inline-flex size-8 flex-none cursor-pointer items-center justify-center rounded-full text-text-secondary hover:bg-surface-muted hover:text-foreground"
      >
        <XIcon aria-hidden="true" className="size-4" />
      </button>
    </div>
  )
}
