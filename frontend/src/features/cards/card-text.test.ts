import { describe, expect, it } from "vitest"

import { chunkCount, dropFlaggedSentences, dropFlaggedStory, flaggedValues, formatChars, markUsedNumbers, splitFlagged, storyFlaggedValues } from "./card-text"

const RESULT = "p95 응답 시간이 0.4초로 줄었고 주문 1건당 쿼리가 21회에서 1회가 됐다. 관련 문의도 [확인 필요: 30%] 줄었다."

describe("확인 필요 표시", () => {
  it("글자와 표시로 나눈다", () => {
    expect(splitFlagged("문의도 [확인 필요: 30%] 줄었다.")).toEqual([
      { text: "문의도 " },
      { text: "확인 필요: 30%", flagged: true },
      { text: " 줄었다." },
    ])
    expect(splitFlagged("표시 없음")).toEqual([{ text: "표시 없음" }])
  })
  it("표시된 숫자를 꺼낸다", () => {
    expect(flaggedValues(RESULT)).toEqual(["30%"])
    expect(flaggedValues("없음")).toEqual([])
  })
  it("표시가 든 문장만 지운다", () => {
    expect(dropFlaggedSentences(RESULT)).toBe("p95 응답 시간이 0.4초로 줄었고 주문 1건당 쿼리가 21회에서 1회가 됐다.")
    expect(dropFlaggedSentences("그대로다.")).toBe("그대로다.")
  })
  it("소수점과 겹친 문장부호에서는 문장을 끊지 않는다", () => {
    expect(dropFlaggedSentences("응답이 [확인 필요: 1.5배] 빨라졌다. 그대로다.")).toBe("그대로다.")
    expect(dropFlaggedSentences("문의가 [확인 필요: 30%] 줄었고 응답은 0.4초다. 끝.")).toBe("끝.")
    expect(dropFlaggedSentences("정말?! 좋다.")).toBe("정말?! 좋다.")
  })
})

describe("markUsedNumbers", () => {
  it("p95, N+1 같은 이름 속 숫자는 숫자로 보지 않는다", () => {
    expect(markUsedNumbers("p95 1.8초, N+1 제거", "p95 1.8초, N+1")).toEqual([
      [{ text: "p95 " }, { text: "1.8초", flagged: true }, { text: ", N+1 제거" }],
    ])
  })
  it("1건당처럼 단위로 쓴 수는 굵게 하지 않는다", () => {
    expect(markUsedNumbers("주문 1건에 쿼리 21회", "주문 1건당 DB 쿼리 21회 → 1회")).toEqual([
      [{ text: "주문 1건에 쿼리 " }, { text: "21회", flagged: true }],
    ])
  })
  it("카드에 쓰인 숫자만 굵게 나누고 문단을 나눈다", () => {
    const paragraphs = markUsedNumbers("p95 1.8초.\n쿼리 21회, 다른 숫자 7회.", "1.8초 → 0.4초, 21회 → 1회")
    expect(paragraphs).toEqual([
      [{ text: "p95 " }, { text: "1.8초", flagged: true }, { text: "." }],
      [{ text: "쿼리 " }, { text: "21회", flagged: true }, { text: ", 다른 숫자 7회." }],
    ])
  })
})

describe("조각과 글자 수", () => {
  it("5,000자씩 나눈다. 빈 자료도 한 조각", () => {
    expect(chunkCount(12480)).toBe(3)
    expect(chunkCount(5000)).toBe(1)
    expect(chunkCount(0)).toBe(1)
  })
  it("글자 수를 쉼표로", () => {
    expect(formatChars(12480)).toBe("12,480자")
  })
})

describe("이야기 여섯 칸의 확인 필요", () => {
  const star = { situation: "", task: "하루 3만 건을 옮겼다.", obstacle: "", action: "큐를 나눴다.\n실패는 다시 보냈다. ", result: RESULT, learning: "작업을 쪼개면 [확인 필요: 2배] 빨라진다. 실행 계획부터 본다." }

  it("결과뿐 아니라 배운 점의 표시도 찾는다", () => {
    expect(storyFlaggedValues(star)).toEqual(["30%", "2배"])
  })

  it("표시가 든 문장만 칸마다 지우고, 표시가 없는 칸은 줄바꿈과 공백까지 그대로 둔다", () => {
    expect(dropFlaggedStory(star)).toEqual({
      ...star,
      result: "p95 응답 시간이 0.4초로 줄었고 주문 1건당 쿼리가 21회에서 1회가 됐다.",
      learning: "실행 계획부터 본다.",
    })
  })
})
