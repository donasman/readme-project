import type { ReactNode } from "react"

import { CircleAlertIcon, CircleCheckIcon } from "@/shared/icons"
import { joinClasses } from "@/shared/lib/utils"

/** 하이라이트 알림 상자(14px): 굵은 제목 아래 설명과 링크. 무언가 하고 난 결과로 나타나므로 화면 읽기에 알린다. */
export function Notice({ title, children }: { title: ReactNode; children?: ReactNode }) {
  return (
    <div role="status" className="flex flex-col gap-2 rounded-control bg-highlight-subtle px-5 py-4 text-sm">
      <strong className="font-bold">{title}</strong>
      {children}
    </div>
  )
}

const TONES = {
  /** 오류 */
  danger: { Icon: CircleAlertIcon, color: "text-danger" },
  /** 확인할 것: 읽지 못한 칸, 확인 필요 숫자 */
  warning: { Icon: CircleAlertIcon, color: "text-warning" },
  /** 끝남. 바뀐 결과라 화면 읽기에 알린다 */
  success: { Icon: CircleCheckIcon, color: "text-success" },
} as const

/** 아이콘이 붙은 짧은 알림 줄(13px). 칸 아래에 둘 때는 id를 주고 입력의 aria-describedby로 잇는다. */
export function IconNote({ id, tone, children }: { id?: string; tone: keyof typeof TONES; children: ReactNode }) {
  const { Icon, color } = TONES[tone]
  return (
    <p id={id} role={tone === "success" ? "status" : undefined} className="flex items-start gap-1.5 text-label-sm font-medium">
      <Icon aria-hidden="true" className={joinClasses("mt-px size-4 flex-none", color)} />
      <span>{children}</span>
    </p>
  )
}
