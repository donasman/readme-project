/**
 * 그룹 색(11차 시안): 그룹 표시(GroupMark)와 그룹 머리 영역의 바탕에 쓴다. 모두 흰 글자와 6:1 넘게 대비된다.
 * 강조색(남색)은 브랜드 색이라 넣지 않았다(한 그룹만 공식 그룹처럼 보인다).
 */
export const GROUP_COLORS = ["#0e5f5c", "#2f6b34", "#a03c25", "#7a2e5c", "#1f5f99", "#7a5a12"] as const

/**
 * 그룹 id로 색 하나를 고른다. 같은 id면 누구에게나 같은 색이다. 글자 코드에 자리 번호를 곱해 더해서
 * 서버의 연이은 번호(1~6)도, 가짜 서버의 글자 id도 서로 다른 색이 된다.
 * shortcut: 그룹장이 색을 고를 수 없다, 서버에 그룹 색 칸이 생기면 그 값을 쓴다
 */
export function groupColor(id: string): string {
  let sum = 0
  for (let i = 0; i < id.length; i++) sum += id.charCodeAt(i) * (i + 1)
  return GROUP_COLORS[sum % GROUP_COLORS.length]
}
