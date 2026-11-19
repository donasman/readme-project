// 카드 이야기 칸의 순서와 선택 칸. 카드 화면과 카드 미리 보기 창이 함께 쓴다.
import type { Star } from "./types"

/** 이야기 칸의 이름(화면에 보이는 순서, 경험카드 문서 4.8). STAR 넷 사이와 뒤에 어려움·배운 점이 온다. */
export const STORY_PARTS: [keyof Star, string][] = [
  ["situation", "상황"],
  ["task", "과제"],
  ["obstacle", "어려움"],
  ["action", "행동"],
  ["result", "결과"],
  ["learning", "배운 점"],
]

/** 어려움과 배운 점은 선택이다. 자료에 없으면 비워 두고, 비었으면 상세와 확인에 보이지 않는다 */
export const isOptionalPart = (key: keyof Star): boolean => key === "obstacle" || key === "learning"
