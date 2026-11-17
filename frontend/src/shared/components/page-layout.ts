import { joinClasses } from "@/shared/lib/utils"

// 화면의 배치 클래스. 요소 종류(div, section, form, Panel …)가 화면마다 달라 부품 대신 클래스 묶음으로 둔다.

/** 화면 머리: 왼쪽 제목 묶음과 오른쪽 버튼. 좁으면 버튼이 제목 아래로 내려간다. */
export const pageHead = "flex flex-wrap items-end justify-between gap-x-6 gap-y-4"

/** 2단 배치의 줄: 넓으면 본문 칸과 옆 칸을 나란히 두고, 좁으면 옆 칸이 본문 아래로 간다. */
export const columns = "flex flex-wrap items-start gap-6"

/** 2단 배치의 본문 칸: 560px부터 남는 폭을 모두 차지한다. 칸 안 간격은 부르는 쪽이 준다. */
export const mainColumn = "flex min-w-0 flex-[999_1_560px] flex-col"

const SIDE_WIDTHS = { 300: "flex-[1_1_300px]", 320: "flex-[1_1_320px]", 340: "flex-[1_1_340px]" } as const

/** 2단 배치의 옆 칸: 시안마다 정한 폭(경험카드 300, 그룹 공고 상세 340, 그 밖 320)에서 시작한다. */
export const sideColumn = (width: keyof typeof SIDE_WIDTHS) => joinClasses("flex min-w-0 flex-col", SIDE_WIDTHS[width])
