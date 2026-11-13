import { Dialog } from "@base-ui/react/dialog"
import type { ReactNode } from "react"

import { Button } from "@/components/ui/button"
import { joinClasses } from "@/shared/lib/utils"

// 리드미의 창(시안 7차 "창 · …"): 가운데 440px, 옅은 검은 덮개, 제목 18px, 오른쪽 아래 버튼들.
// 열고 닫기, 첫 칸으로 초점 옮기기, Esc로 닫기, 닫으면 연 버튼으로 초점 돌려주기는 base-ui Dialog가 한다.

interface AppDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: ReactNode
  /** 제목 아래 작은 회색 글(대상 공고, 그룹 이름 …) */
  subtitle?: ReactNode
  children?: ReactNode
  /** 오른쪽 아래 버튼들. 취소는 DialogCancel로 넣는다 */
  actions: ReactNode
  /** 화면 위쪽(120px)에 붙인다. 기본은 가운데 */
  placement?: "center" | "top"
}

/** 확인을 받거나 짧게 고치는 창. 본문(children)은 세로로 20px씩 띄워 쌓는다. */
export function AppDialog({ open, onOpenChange, title, subtitle, children, actions, placement = "center" }: AppDialogProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-black/45" />
        <Dialog.Popup
          className={joinClasses(
            "fixed left-1/2 z-50 flex w-[calc(100%-2rem)] max-w-[440px] -translate-x-1/2 flex-col gap-5 overflow-y-auto rounded-panel border border-border-subtle bg-surface-raised p-6 text-foreground shadow-floating outline-none",
            placement === "top" ? "top-[120px] max-h-[calc(100svh-136px)]" : "top-1/2 max-h-[calc(100svh-2rem)] -translate-y-1/2"
          )}
        >
          {subtitle ? (
            <div className="flex flex-col gap-2">
              <Dialog.Title className="text-lg font-bold">{title}</Dialog.Title>
              <p className="text-sm text-text-secondary">{subtitle}</p>
            </div>
          ) : (
            <Dialog.Title className="text-lg font-bold">{title}</Dialog.Title>
          )}
          {children}
          <div className="flex flex-wrap justify-end gap-2">{actions}</div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

/** 창의 취소 버튼: 창을 닫기만 한다. */
export function DialogCancel({ children = "취소" }: { children?: ReactNode }) {
  return <Dialog.Close render={<Button type="button" variant="outline" />}>{children}</Dialog.Close>
}
