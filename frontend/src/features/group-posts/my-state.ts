import type { MyState } from "./types"

/** 내가 이 공고의 관심 멤버로 보이는지: 관심을 켰거나 지원 상태가 있다. */
export function isInterestedOrApplied(me: MyState): boolean {
  return me.interested || me.status !== null
}
