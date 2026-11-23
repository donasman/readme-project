// 가짜 서버의 내 공고 행(서버의 user_postings): 공고마다 내 결정·단계·결과와 메모. 값은 시안 8차 "내 공고"의 9건이다.
// 내 공고와 지원 현황(my-db.ts), 그룹 공고의 내 상태(db.ts), 메일 확인함의 반영(mail-db.ts)이 모두 이것을 읽고 쓴다.
// db.ts와 my-db.ts가 서로 부르지 않게 따로 둔다. 이 폴더는 shared와 타입만 불러온다.
import type { MyState } from "@/domain/posting/my-state"
import type { PostSection } from "@/domain/posting/types"
import type { MyPosting } from "@/features/my-posts/types"
import { NotFoundError } from "@/shared/lib/errors"

/** 내 공고 하나: 어느 그룹 공고들에 걸려 있는지와 내 상태. */
export interface Entry extends MyState {
  id: string
  /** [그룹, 그 그룹의 공고 id]. 첫 것을 본체로 읽는다 */
  refs: [string, string][]
  /** 그룹을 나가 본체를 못 읽을 때 쓰는 마지막 값. 그룹 없이 넣은 공고는 처음부터 이 값이다 */
  snapshot: Pick<MyPosting, "company" | "title" | "deadline"> | null
  memo: string
  /** 경험카드 우선순위(서버의 user_posting_cards). 첫째가 앞. 없으면 빈 목록으로 본다 */
  priority?: { cardId: string; reason: string | null }[]
  /** 그룹 없이 넣은 공고의 나머지 본체(어느 그룹 공고에도 걸려 있지 않다) */
  own?: { applyAt: string | null; sourceUrl: string | null; sections: PostSection[]; keywords: string[] }
}

const applied = (stage: MyState["stage"], result: MyState["result"]): MyState => ({ decision: "applied", stage, result })
const decided = (decision: MyState["decision"]): MyState => ({ decision, stage: null, result: null })
const entry = (id: string, ref: [string, string], s: MyState, memo = ""): Entry => ({ id, refs: [ref], snapshot: null, memo, ...s })

function createEntries(): Entry[] {
  return [
    entry("p-mes1", ["mes", "mes1"], applied("document", "in_progress")),
    entry("p-be1", ["be", "be1"], decided("undecided")),
    entry("p-pub1", ["pub", "pub1"], applied("test", "in_progress"), "필기는 NCS와 전산 전공. 작년 기출 범위 다시 보기."),
    entry("p-be2", ["be", "be2"], applied("document", "in_progress")),
    entry("p-be3", ["be", "be3"], decided("hold")),
    entry("p-be6", ["be", "be6"], decided("undecided")),
    entry("p-be5", ["be", "be5"], decided("undecided")),
    entry("p-be7", ["be", "be7"], applied("document", "failed")),
    entry("p-be8", ["be", "be8"], applied("interview_1", "in_progress")),
  ]
}

let store: Entry[] | null = null
export const entries = () => (store ??= createEntries())

let nextId = 1

/** 내 공고 행을 지운다. 관심과 일정은 부르는 쪽(my-db.ts)이 정리한다. */
export function removeEntry(id: string): void {
  const e = rawEntry(id)
  store = entries().filter((x) => x !== e)
}

/** 그룹 없이 넣은 공고의 행을 맨 앞에 만든다(서버처럼 넣은 시각의 역순). 지원 상태는 미정으로 시작한다. */
export function addEntry(snapshot: NonNullable<Entry["snapshot"]>, own: NonNullable<Entry["own"]>): Entry {
  const e: Entry = { id: `p-new${nextId++}`, refs: [], snapshot, memo: "", own, decision: "undecided", stage: null, result: null }
  entries().unshift(e)
  return e
}

export function rawEntry(id: string): Entry {
  // 서버는 공고 본체 id 하나를 쓴다(그룹 공고의 id가 곧 내 공고 id). 가짜 서버는 내 공고 id가 따로라 그룹 공고 id로도 찾는다
  const found = entries().find((e) => e.id === id || e.refs.some(([, postId]) => postId === id))
  if (!found) throw new NotFoundError("공고")
  return found
}

/** 내 공고 행의 지금 상태. */
export function stateOf(id: string): MyState {
  const { decision, stage, result } = rawEntry(id)
  return { decision, stage, result }
}

// 서버의 ApplicationStatus.label과 같은 글자(domain/posting/my-state.ts의 statusLabel과도 같다).
// 가짜 서버는 domain을 불러오지 않으므로 따로 둔다.
const STAGE_TEXT = { document: "서류", test: "필기", interview_1: "1차 면접", interview_2: "2차 면접", final: "최종" } as const

/** 그룹 공고의 내 상태 글자(me.status). 서버처럼 지원함일 때만 주고, 내 공고 행이 없거나 지원함이 아니면 null. */
export function statusOf(groupId: string, postId: string): string | null {
  const e = entries().find((x) => x.refs.some(([g, p]) => g === groupId && p === postId))
  if (!e || e.decision !== "applied") return null
  const stage = e.stage ?? "document"
  const result = e.result ?? "in_progress"
  if (result === "in_progress") return stage === "document" ? "지원함" : `${STAGE_TEXT[stage]} 단계`
  return `${STAGE_TEXT[stage]} ${result === "passed" ? "합격" : "불합격"}`
}

/** 그룹을 나가거나 공고가 내려가기 직전에 부른다: 그 공고에 걸린 내 공고 행에 마지막 회사·공고명·마감을 남긴다. */
export function keepSnapshot(groupId: string, post: Pick<MyPosting, "company" | "title" | "deadline"> & { id: string }): void {
  for (const e of entries()) {
    if (e.refs.some(([g, p]) => g === groupId && p === post.id)) {
      e.snapshot = { company: post.company, title: post.title, deadline: structuredClone(post.deadline) }
    }
  }
}
