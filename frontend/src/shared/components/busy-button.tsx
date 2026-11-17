import { useState, type ComponentProps } from "react"

import { Button } from "@/components/ui/button"

/**
 * 누르면 일이 끝날 때까지 막히는 버튼(지우기, 나가기, 반영 …). 두 번 눌러 같은 요청이 두 번 가고 두 번째가 404로 실패하는 일을 막는다.
 * 일의 오류는 삼키지 않고 그대로 던져 화면 아래 알림(app/action-error.tsx)이 보인다.
 */
export function BusyButton({ onClick, disabled, ...props }: Omit<ComponentProps<typeof Button>, "onClick"> & { onClick: () => Promise<unknown> }) {
  const [busy, setBusy] = useState(false)
  return (
    <Button
      {...props}
      disabled={disabled || busy}
      onClick={async () => {
        setBusy(true)
        try {
          await onClick()
        } finally {
          setBusy(false)
        }
      }}
    />
  )
}
