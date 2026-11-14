import type { ComponentProps } from "react"

import { joinClasses } from "@/shared/lib/utils"

/** 18px 체크·라디오(브라우저 기본 모양에 강조색). 글과 위를 맞출 때(items-start)는 className="mt-0.5"를 준다. */
export function ChoiceInput({ className, ...props }: ComponentProps<"input"> & { type: "checkbox" | "radio" }) {
  return <input className={joinClasses("size-[18px] flex-none accent-primary", className)} {...props} />
}
