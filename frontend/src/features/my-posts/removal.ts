// 내 공고에서 빼기(12차 시안, 공고 문서 4.2)의 확인 창에 적을 것. 서버는 지원 상태, 메모, 전형 일정을 지우고 내 그룹들에서 켠 관심을 끈다.
import { statusDetail } from "@/domain/posting/my-state"

import type { MyPostingDetail } from "./types"

export interface Removal {
  /** 지워지는 것. strong은 굵게 보일 값(지원 상태) */
  erased: { label: string; strong: string | null }[]
  /** 관심이 꺼지는 그룹 이름 */
  groups: string[]
  /** 그룹에 올라간 공고라 공고와 댓글이 남는다 */
  inGroups: boolean
}

/** 지원 상태가 미정이고 메모와 전형 일정이 없으면 erased가 비어 창이 짧은 글만 보인다. memo는 저장한 메모다. */
export function removalOf(posting: MyPostingDetail, memo: string, scheduleCount: number): Removal {
  return {
    erased: [
      ...(posting.decision === "undecided" ? [] : [{ label: "지원 상태", strong: statusDetail(posting) }]),
      ...(memo.trim() ? [{ label: "나만 보는 메모", strong: null }] : []),
      ...(scheduleCount > 0 ? [{ label: `전형 일정 ${scheduleCount}건`, strong: null }] : []),
    ],
    groups: posting.groupStates.filter((g) => g.interested).map((g) => g.name),
    inGroups: posting.groupStates.length > 0,
  }
}
