import type { ReactNode } from "react"

import { AppDialog, DialogCancel } from "@/shared/components/app-dialog"
import { BusyButton } from "@/shared/components/busy-button"
import { IconNote } from "@/shared/components/notice"

import type { Removal } from "../removal"

/** 창의 한 줄: 이름(지워지는 것 …)과 그 아래 값들. 둘째 줄부터 위에 가는 줄. */
function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5 border-t border-border-subtle px-4 py-3.5 first:border-t-0">
      <dt className="text-[13px] font-bold text-text-secondary">{label}</dt>
      {children}
    </div>
  )
}

interface RemoveDialogProps {
  open: boolean
  onClose: () => void
  /** "회사 · 공고명" */
  name: string
  removal: Removal
  onConfirm: () => Promise<void>
}

/** 내 공고에서 빼기(12차 시안 "창 · 내 공고에서 빼기"): 지워지는 것, 꺼지는 것, 남는 것. 지울 것이 없으면 짧은 글만. */
export function RemoveDialog({
  open,
  onClose,
  name,
  removal,
  onConfirm,
}: RemoveDialogProps) {
  const groups = removal.groups.join(", ")
  return (
    <AppDialog
      open={open}
      onOpenChange={(next) => !next && onClose()}
      title="내 공고에서 뺄까요?"
      subtitle={name}
      actions={
        <>
          <DialogCancel />
          <BusyButton type="button" variant="danger" onClick={onConfirm}>
            빼기
          </BusyButton>
        </>
      }
    >
      {removal.erased.length === 0 ? (
        <>
          <p className="text-sm">
            지원 상태가 미정이고 메모와 전형 일정이 없는 공고입니다.
            {groups && ` 빼면 ${groups} 그룹에서 켠 관심이 꺼집니다.`}
          </p>
          {removal.inGroups && (
            <p className="text-sm">
              공고와 댓글은 그룹에 남습니다. 그룹 공고에서 관심을 켜면 다시
              들어옵니다.
            </p>
          )}
        </>
      ) : (
        <>
          <dl className="flex flex-col rounded-control border border-border-subtle">
            <Row label="지워지는 것">
              {removal.erased.map((e) => (
                <dd key={e.label} className="text-sm">
                  {e.label}
                  {e.strong && (
                    <strong className="ml-1 font-bold">{e.strong}</strong>
                  )}
                </dd>
              ))}
            </Row>
            {groups && (
              <Row label="꺼지는 것">
                <dd className="text-sm">{groups} 그룹에서 켠 관심</dd>
              </Row>
            )}
            {removal.inGroups && (
              <Row label="남는 것">
                <dd className="text-sm">
                  그룹에 올라간 공고와 댓글, 다른 멤버의 관심
                </dd>
              </Row>
            )}
          </dl>
          <IconNote tone="warning">
            되돌릴 수 없습니다. 다시 넣으면 지원 상태는 미정부터 시작합니다.
          </IconNote>
        </>
      )}
    </AppDialog>
  )
}
