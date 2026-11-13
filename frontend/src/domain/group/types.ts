// 그룹의 데이터 모양. 서버가 생기면 응답이 이 모양으로 온다고 보고 화면을 짠다.

/** 그룹 멤버. 보는 사람(나)도 멤버다. */
export interface Member {
  id: string
  name: string
}

/** 그룹 공고의 지원 상태를 그룹에 얼마나 보일지. 그룹마다 따로 고르고 기본은 "applied". */
export type Disclosure = "interest" | "applied" | "progress"

/** 내가 속한 그룹. */
export interface Group {
  id: string
  name: string
  /** 그룹 표시 글자. 동그라미 안에 넣는다 */
  mark: string
  /** 보는 사람 포함 */
  members: Member[]
  leaderId: string
  /** 진행 중인 공고 수. 그룹 전환 메뉴의 "공고 N" */
  openPostCount: number
  /** 내가 이 그룹에 정한 공개 범위 */
  myDisclosure: Disclosure
}
