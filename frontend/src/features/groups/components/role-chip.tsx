import { Pill } from "@/shared/components/pill"

/** 멤버 옆 알약. 그룹장은 옅은 강조색, 나는 하이라이트색(내 것에만 쓰는 색). */
export function RoleChip({ role }: { role: "leader" | "me" }) {
  return role === "leader" ? <Pill tone="accent">그룹장</Pill> : <Pill tone="highlight">나</Pill>
}
