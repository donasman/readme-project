import * as React from "react"
import { cn } from "cn"

function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        // 리드미 모양: radius-control(rounded-lg), 안쪽 12px 16px, 16px 글자, 세로로만 늘림. rows 로 높이를 정한다.
        // 포커스는 링 없이 테두리만 강조색(Arc는 글자 입력칸에 외곽선을 그리지 않는다).
        "flex w-full resize-y rounded-lg border border-input bg-transparent px-4 py-3 text-base leading-body transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring disabled:cursor-not-allowed disabled:bg-input/50 disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 dark:bg-input/30 dark:disabled:bg-input/80 dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40",
        className
      )}
      {...props}
    />
  )
}

export { Textarea }
