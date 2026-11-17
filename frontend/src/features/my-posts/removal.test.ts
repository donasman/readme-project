import { describe, expect, it } from "vitest"

import { removalOf } from "./removal"
import type { MyPostingDetail } from "./types"

const group = (name: string, interested: boolean) => ({ id: name, name, mark: name[0], postId: "1", interested, commentCount: 0, disclosure: "applied" as const })
const posting = (patch: Partial<MyPostingDetail>) => ({ decision: "undecided", stage: null, result: null, groupStates: [], ...patch }) as MyPostingDetail

describe("removalOf", () => {
  it("지원 상태, 저장한 메모, 전형 일정을 지워지는 것으로 적고 관심 켠 그룹만 꺼지는 것으로 적는다", () => {
    const removal = removalOf(posting({ decision: "applied", stage: "test", result: "in_progress", groupStates: [group("공기업", true), group("백엔드", false)] }), "기출", 1)
    expect(removal).toEqual({
      erased: [
        { label: "지원 상태", strong: "지원함 · 필기 단계" },
        { label: "나만 보는 메모", strong: null },
        { label: "전형 일정 1건", strong: null },
      ],
      groups: ["공기업"],
      inGroups: true,
    })
  })

  it("미정이고 메모와 일정이 없으면 지워지는 것이 없다", () => {
    expect(removalOf(posting({}), "  ", 0)).toEqual({ erased: [], groups: [], inGroups: false })
  })
})
