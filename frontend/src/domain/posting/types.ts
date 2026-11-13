// 공고 본체의 값 가운데 두 기능 이상이 쓰는 것(그룹 공고, 내 공고). 날짜는 한국 시간 기준 "YYYY-MM-DD".

/** 마감. 날짜를 못 읽었으면 unknown, 상시·수시 채용이면 always. */
export type Deadline =
  | { kind: "date"; date: string }
  | { kind: "always" }
  | { kind: "unknown" }

/** 공고 내용의 한 묶음(주요 업무, 필수 요건 …). */
export interface PostSection {
  title: string
  items: string[]
}

/** 다른 화면이 그룹 공고 목록으로 보내며 "마감일 넣기" 창을 바로 열게 할 때 넘기는 값(홈의 확인 필요). */
export interface FillDeadlineState {
  fillDeadline: string
}
