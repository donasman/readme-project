import type { Member } from "./types"

/** 멤버의 이름. 탈퇴한 사람은 서버가 null로 보낸다(올린 사람, 댓글 쓴 사람. 공고 문서 8절 3번). */
export const memberName = (member: Member | null) => member?.name ?? "탈퇴한 멤버"
