/** 로그인한 사람(보는 사람). 그룹 안에서는 멤버 한 명이기도 하다. */
export interface Viewer {
  id: string
  name: string
}

// shortcut: 표시 이름의 길이는 프런트가 정했다(DB는 50자), 로그인 설계가 길이를 정하면 맞춘다
/** 표시 이름의 최대 길이. 가입과 내 정보가 함께 쓴다. */
export const NAME_MAX_LENGTH = 20
