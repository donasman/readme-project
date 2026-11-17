import type { ReactNode } from "react"

import { joinClasses } from "@/shared/lib/utils"

// 입력 칸 한 묶음: 이름표(14px), 입력, 그 아래 작은 글(안내, 근거, 오류). 입력은 children으로 넣고,
// 아래 글이 있으면 입력에 aria-describedby={noteId(id)}를 단다.

/** 아래 글의 id. */
export const noteId = (id: string) => `${id}-note`

interface FormFieldProps {
  id: string
  label: ReactNode
  /** 이름표 오른쪽 끝(글자 수 등) */
  aside?: ReactNode
  /** 입력 아래 글. FieldHint, FieldError 등을 넣는다 */
  note?: ReactNode
  className?: string
  children: ReactNode
}

export function FormField({ id, label, aside, note, className, children }: FormFieldProps) {
  const labelElement = (
    <label htmlFor={id} className="text-sm font-medium">
      {label}
    </label>
  )
  return (
    <div className={joinClasses("flex flex-col gap-1.5", className)}>
      {aside ? (
        <div className="flex items-baseline justify-between gap-3">
          {labelElement}
          {aside}
        </div>
      ) : (
        labelElement
      )}
      {children}
      {note}
    </div>
  )
}

/** 입력 아래 안내(13px 회색). */
export function FieldHint({ id, children }: { id: string; children: ReactNode }) {
  return (
    <p id={noteId(id)} className="text-label-sm text-text-secondary">
      {children}
    </p>
  )
}

/** 입력 아래 오류(13px 빨강). 누른 버튼에 초점이 남아도 화면 읽기가 읽게 alert로 둔다. */
export function FieldError({ id, children }: { id: string; children: ReactNode }) {
  return (
    <p id={noteId(id)} role="alert" className="text-label-sm font-medium text-danger">
      {children}
    </p>
  )
}
