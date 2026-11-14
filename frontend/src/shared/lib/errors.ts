import { objectParticle } from "./korean"

/** 찾는 것이 없을 때 던진다. what은 못 찾은 것의 이름이다("그룹", "공고"). 앱의 오류 화면이 "찾을 수 없습니다"와 이 메시지를 보여 준다. */
export class NotFoundError extends Error {
  constructor(what: string) {
    super(`${what}${objectParticle(what)} 찾을 수 없습니다.`)
    this.name = "NotFoundError"
  }
}
