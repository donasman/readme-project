export { cn } from "cn"

/**
 * 클래스를 지우지 않고 잇는다. cn은 겹치는 Tailwind 클래스를 지우는데, 리드미 토큰 이름(text-label-sm 등)은
 * 글자 색으로 읽혀 뒤의 text-색 클래스와 함께 있으면 지워진다. 공용 부품의 기본 클래스는 이것으로 잇는다.
 */
export function joinClasses(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(" ")
}
