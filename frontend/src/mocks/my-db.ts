// 가짜 서버의 내 공고 쪽: 관심을 켜거나 직접 넣은 공고와 내 지원 상태, 메모, 맞는 카드, 홈의 모음.
// 공고 본체(회사, 공고명, 마감, 내용)는 그룹 공고(db.ts)에서 그때그때 읽는다. 그룹에서 마감일을 넣으면 여기도 바뀐다.
// 내 공고 조회 API는 아직 설계에 없다(화면 API 대응 3.3의 10번). 이 폴더는 shared와 타입만 불러온다.
import type { GroupPostDetail } from "@/features/group-posts/types"
import type { CardMatch, GroupNews, HomeData, MyPosting, MyPostingDetail, MyState, PostingGroup } from "@/features/my-posts/types"
import { NotFoundError } from "@/shared/lib/errors"

import { listCards, listUnverifiedCards } from "./cards-db"
import { findPost, listGroups, today } from "./db"

/** 내 공고 하나: 어느 그룹 공고들에 걸려 있는지와 내 상태. 시안 8차 "내 공고"의 9건이다. */
interface Entry extends MyState {
  id: string
  /** [그룹, 그 그룹의 공고 id]. 첫 것을 본체로 읽는다 */
  refs: [string, string][]
  /** 그룹을 나가 본체를 못 읽을 때 쓰는 마지막 값 */
  snapshot: Pick<MyPosting, "company" | "title" | "deadline"> | null
  memo: string
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

/** 상세에만 보이는 값. 예시 공사(시안 8차 "공고 상세")의 공고 내용과 맞는 카드를 옮겼다. 그 밖의 공고는 그룹 공고의 내용을 쓴다. */
const DETAILS: Record<string, Partial<Pick<MyPostingDetail, "applyAt" | "sections" | "keywords" | "unmatched">> & { matches?: [number, string[]][] }> = {
  "p-pub1": {
    applyAt: "기관 채용 홈페이지",
    sections: [
      { title: "주요 업무", items: ["기관 업무 시스템 운영과 유지보수", "정보보안 점검과 전산 자원 관리"] },
      { title: "필수 요건", items: ["정보처리기사 또는 같은 수준의 자격", "Java 기반 웹 시스템 개발 경험"] },
      { title: "우대 사항", items: ["네트워크 운영 경험", "SQL 튜닝 경험"] },
    ],
    keywords: ["Java", "Spring", "SQL", "정보보안", "네트워크"],
    matches: [
      [1, ["Java", "SQL"]],
      [5, ["Java", "Spring", "정보보안"]],
      [2, ["SQL"]],
    ],
    unmatched: ["네트워크 운영 경험"],
  },
}

/** 메일에서 온 상태 변경(9차 "메일 확인함"이 생기면 그쪽 값을 읽는다). */
const MAIL_CHANGES = { count: 1, first: "예시 보안 면접 안내" }

/** 홈의 그룹 소식(시안 8차 "홈"의 두 줄). 무엇을 소식으로 볼지는 아직 정하지 않아 예시로 둔다. */
const NEWS: { groupId: string; text: string; postId: string | null }[] = [
  { groupId: "be", text: "새 공고 2건", postId: null },
  { groupId: "pub", text: "나래 님이 예시 공사 공고에 댓글을 남겼습니다", postId: "pub1" },
]

let store: Entry[] | null = null
const entries = () => (store ??= createEntries())
const copy = <T>(value: T): T => structuredClone(value)

function rawEntry(id: string): Entry {
  const found = entries().find((e) => e.id === id)
  if (!found) throw new NotFoundError("공고")
  return found
}

/** 내가 아직 속한 그룹의 공고들. 나간 그룹의 공고는 읽지 못한다. */
function readPosts(e: Entry): { group: PostingGroup; post: GroupPostDetail }[] {
  const groups = listGroups()
  return e.refs.flatMap(([groupId, postId]) => {
    const g = groups.find((x) => x.id === groupId)
    if (!g) return []
    try {
      return [{ group: { id: g.id, name: g.name, mark: g.mark, postId }, post: findPost(groupId, postId) }]
    } catch {
      return []
    }
  })
}

function toPosting(e: Entry): MyPosting {
  const found = readPosts(e)
  const body = found[0]?.post
  if (body) e.snapshot = { company: body.company, title: body.title, deadline: copy(body.deadline) }
  const { company, title, deadline } = e.snapshot ?? { company: "", title: "", deadline: { kind: "unknown" as const } }
  return { id: e.id, company, title, deadline, groups: found.map((f) => f.group), decision: e.decision, stage: e.stage, result: e.result }
}

/** 내 공고 목록. */
export const listMyPostings = (): MyPosting[] => entries().map(toPosting)

/** 내 공고 상세. */
export function getMyPosting(id: string): MyPostingDetail {
  const e = rawEntry(id)
  const posting = toPosting(e)
  const found = readPosts(e)
  const body = found[0]?.post
  const extra = DETAILS[id] ?? {}
  const cards = listCards()
  const keywords = extra.keywords ?? body?.keywords ?? []
  // 맞는 카드: 예시 공고는 시안 값, 그 밖에는 공고 키워드와 카드 태그가 겹치는 카드(맞추기 규칙은 아직 없다)
  const matches: CardMatch[] = extra.matches
    ? extra.matches.flatMap(([cardId, overlap]) => {
        const card = cards.find((c) => c.id === cardId)
        return card ? [{ cardId, title: card.title, overlap }] : []
      })
    : cards
        .map((c) => ({ cardId: c.id, title: c.title, overlap: c.tags.filter((t) => keywords.some((k) => k.toLowerCase() === t.toLowerCase())) }))
        .filter((m) => m.overlap.length > 0)
        .sort((a, b) => b.overlap.length - a.overlap.length)
  return {
    ...posting,
    applyAt: extra.applyAt ?? body?.applyAt ?? null,
    sourceUrl: body?.sourceUrl ?? null,
    sections: copy(extra.sections ?? body?.sections ?? []),
    keywords: [...keywords],
    memo: e.memo,
    matches,
    unmatched: [...(extra.unmatched ?? [])],
    groupStates: found.map(({ group, post }) => ({
      ...group,
      interested: post.me.interested,
      commentCount: post.commentCount,
      disclosure: listGroups().find((g) => g.id === group.id)?.myDisclosure ?? "applied",
    })),
  }
}

/** 내 지원 상태 바꾸기(PUT /api/me/postings/{postingId}). 단계와 결과는 지원함일 때만 함께 둔다. */
export function updateMyState(id: string, state: MyState): MyPosting {
  const e = rawEntry(id)
  const ok = state.decision === "applied" ? state.stage !== null && state.result !== null : state.stage === null && state.result === null
  if (!ok) throw new Error("INVALID_INPUT: 단계와 결과는 지원함일 때만 함께 넣는다")
  Object.assign(e, { decision: state.decision, stage: state.stage, result: state.result })
  return toPosting(e)
}

/** 나만 보는 메모 저장. */
export function saveMemo(id: string, memo: string): void {
  rawEntry(id).memo = memo
}

/** 홈에 모으는 것. */
export function getHome(): HomeData {
  const groups = listGroups()
  const news: GroupNews[] = NEWS.flatMap((n) => {
    const g = groups.find((x) => x.id === n.groupId)
    return g ? [{ group: { id: g.id, name: g.name, mark: g.mark }, text: n.text, postId: n.postId }] : []
  })
  return { today: today(), postings: listMyPostings(), unverifiedCards: listUnverifiedCards().length, mailChanges: { ...MAIL_CHANGES }, news }
}
