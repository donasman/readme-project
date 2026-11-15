// 그룹의 데이터 모양. 서버의 응답(그룹 문서 4.2)에서 id만 문자열로 바꾼 것이다(api.ts의 toGroup).

/** 그룹 멤버. 보는 사람(나)도 멤버다. */
export interface Member {
  id: string
  name: string
}

/** 그룹 안의 멤버: 들어온 날까지(GET /api/groups의 members). */
export interface GroupMember extends Member {
  /** "YYYY-MM-DD"(한국 날짜) */
  joinedAt: string
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
  members: GroupMember[]
  /** 그룹장이 계정을 지우는 사이 드물게 비어 있을 수 있다(그룹 문서 8절 2번) */
  leaderId: string | null
  /** 진행 중인 공고 수. 그룹 전환 메뉴의 "공고 N" */
  openPostCount: number
  /** 내가 이 그룹에 정한 공개 범위 */
  myDisclosure: Disclosure
  /** 진행 중인 공고 가운데 마감일이 가장 가까운 것. 없으면 null */
  nearestDeadline: { date: string; company: string; title: string } | null
}

/** 서버가 보내는 그룹(GET /api/groups의 한 항목). 그룹 만들기, 공개 범위 바꾸기, 초대 수락도 이 모양이다. */
export type GroupResponse = Omit<Group, "id" | "members" | "leaderId"> & {
  id: number
  members: { id: number; name: string; joinedAt: string }[]
  leaderId: number | null
}
