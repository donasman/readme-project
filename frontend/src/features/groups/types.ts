// 내 그룹과 멤버 화면의 데이터 모양. 그룹 자체는 domain/group/types.ts에 있다.

/** 만든 초대 링크와 쓸 수 있는 마지막 날("YYYY-MM-DD"). 링크는 만들 때만 알 수 있다(서버는 토큰의 해시만 둔다). */
export interface CreatedInvite {
  link: string
  expiresAt: string
}
