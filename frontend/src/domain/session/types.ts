/** 로그인한 사람(보는 사람). 그룹 안에서는 멤버 한 명이기도 하다. */
export interface Viewer {
  id: string
  name: string
}

/** GET /api/me의 응답(로그인 문서 4절). 화면은 id, name을 Viewer로 쓰고 내 정보 화면이 나머지를 쓴다. */
export interface MeResponse extends Viewer {
  email: string
  hasPassword: boolean
  providers: string[]
  /** 연결한 구글 계정. 연결할 때 구글이 이메일을 주지 않았으면 email이 null */
  google: { email: string | null } | null
}

/** 표시 이름의 최대 길이. 가입과 내 정보가 함께 쓴다. 서버는 50자까지 받고, 화면은 더 짧게 둔다(로그인 문서 7절 17번). */
export const NAME_MAX_LENGTH = 20
