import { joinClasses } from "@/shared/lib/utils"

/** 멤버 옆 알약(24px). 그룹장은 옅은 강조색, 나는 하이라이트색(내 것에만 쓰는 색). */
export function RoleChip({ role }: { role: "leader" | "me" }) {
  return (
    <span
      className={joinClasses(
        "inline-flex h-6 flex-none items-center rounded-full px-2.5 text-xs font-bold whitespace-nowrap",
        role === "leader" ? "bg-accent-subtle text-accent-strong" : "bg-highlight text-highlight-foreground"
      )}
    >
      {role === "leader" ? "그룹장" : "나"}
    </span>
  )
}
