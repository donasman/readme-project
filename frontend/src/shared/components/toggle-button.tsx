import type { ComponentProps } from "react"

import { Button } from "@/components/ui/button"
import { cn } from "@/shared/lib/utils"

/** 켜진 모양: 옅은 강조색 바탕(헤더 현재 메뉴와 같다). 꺼지면 보통 outline 버튼. */
const pressedClasses =
  "border-transparent bg-accent-subtle text-accent-strong hover:bg-accent-subtle hover:text-accent-strong dark:border-transparent dark:bg-accent-subtle dark:hover:bg-accent-subtle"

interface ToggleButtonProps extends Omit<ComponentProps<typeof Button>, "variant" | "size"> {
  pressed: boolean
}

/** 켜고 끄는 작은 버튼(태그 거르기, 쓸 문항 유형, 결정으로 거르기). aria-pressed로 켜짐을 알린다. */
export function ToggleButton({ pressed, className, ...props }: ToggleButtonProps) {
  return <Button type="button" variant="outline" size="sm" aria-pressed={pressed} className={cn(pressed && pressedClasses, className)} {...props} />
}
