// 백엔드 연결 전 예시 데이터. docs/design/group-posts.dc.html 의 groups()와 group-post.dc.html 의 값을 그대로 옮겼다.
// 이름, 회사, 공고, 댓글은 모두 지어낸 예시다. 보는 사람은 "다온"이고 디자인처럼 오늘은 2026-11-13이다.
// 화면과 api.ts는 이 파일을 직접 읽지 않는다. 같은 폴더의 db.ts만 읽는다.
import type { Disclosure, Member } from "@/domain/group/types"
import type { Comment, Deadline, GroupPostDetail } from "@/features/group-posts/types"

export const EXAMPLE_TODAY = "2026-11-13"

const people = {
  다온: "daon",
  가람: "garam",
  나래: "narae",
  라온: "raon",
  마루: "maru",
  바다: "bada",
  사랑: "sarang",
  자람: "jaram",
  차오름: "chaoreum",
} as const
type Name = keyof typeof people

const member = (name: Name): Member => ({ id: people[name], name })
export const VIEWER = member("다온")

// 디자인의 표기: due는 마감일, "always"(상시), "unknown"(못 읽음). by "나"는 보는 사람.
// fans는 [이름, 지원함 표시] (지원함은 공개 범위가 "지원 여부까지" 이상인 멤버만 붙는다).
// me는 "on" / "off" / 내 지원 상태 값.
interface RawPost {
  id: string
  company: string
  title: string
  due: string
  by: Name | "나"
  at: string
  fans: [Name, 0 | 1][]
  me: string
  comments: number
}
interface RawGroup {
  id: string
  name: string
  mark: string
  members: Name[]
  /** 디자인 파일에는 그룹장이 없다. 예시로 정했다(MES 준비반은 다온이 그룹장이라 그룹장 화면을 볼 수 있다) */
  leader: Name
  myDisclosure: Disclosure
  posts: RawPost[]
}

const RAW: RawGroup[] = [
  {
    id: "be", name: "백엔드 스터디", mark: "백", members: ["가람", "나래", "라온", "마루"], leader: "가람", myDisclosure: "applied",
    posts: [
      { id: "be1", company: "예시 커머스", title: "백엔드 개발자 신입", due: "2026-11-16", by: "가람", at: "2026-11-05", fans: [["가람", 1], ["나래", 1], ["라온", 0]], me: "on", comments: 3 },
      { id: "be2", company: "예시 핀테크", title: "서버 개발 인턴", due: "2026-11-20", by: "나래", at: "2026-11-11", fans: [["가람", 1], ["나래", 0], ["마루", 0]], me: "off", comments: 1 },
      { id: "be3", company: "예시 물류", title: "백엔드 주니어", due: "2026-11-25", by: "라온", at: "2026-11-08", fans: [["라온", 0]], me: "지원함", comments: 0 },
      { id: "be4", company: "예시 게임", title: "플랫폼 서버 신입", due: "2026-12-03", by: "마루", at: "2026-11-12", fans: [["마루", 0]], me: "off", comments: 0 },
      { id: "be5", company: "예시 에듀테크", title: "백엔드 엔지니어", due: "unknown", by: "나", at: "2026-11-13", fans: [], me: "off", comments: 0 },
      { id: "be6", company: "예시 SaaS", title: "서버 개발자", due: "always", by: "가람", at: "2026-11-02", fans: [["가람", 0]], me: "off", comments: 2 },
      { id: "be7", company: "예시 모빌리티", title: "백엔드 신입 공채", due: "2026-11-10", by: "나래", at: "2026-10-28", fans: [["가람", 1], ["나래", 1]], me: "필기 단계", comments: 5 },
      { id: "be8", company: "예시 보안", title: "주니어 서버 개발", due: "2026-11-06", by: "가람", at: "2026-10-25", fans: [["가람", 0]], me: "off", comments: 0 },
    ],
  },
  {
    id: "pub", name: "공기업 전산직", mark: "공", members: ["나래", "바다", "사랑"], leader: "나래", myDisclosure: "applied",
    posts: [
      { id: "pub1", company: "예시 공사", title: "전산직 7급 공채", due: "2026-11-18", by: "나래", at: "2026-11-04", fans: [["나래", 1], ["바다", 1], ["사랑", 0]], me: "on", comments: 4 },
      { id: "pub2", company: "예시 공단", title: "정보보호 신입", due: "2026-11-22", by: "바다", at: "2026-11-12", fans: [["바다", 0]], me: "off", comments: 0 },
      { id: "pub3", company: "예시 대학교", title: "전산직 직원", due: "2026-11-29", by: "사랑", at: "2026-11-07", fans: [["사랑", 0], ["나래", 0]], me: "off", comments: 1 },
    ],
  },
  {
    id: "mes", name: "MES 준비반", mark: "M", members: ["자람", "차오름"], leader: "다온", myDisclosure: "applied",
    posts: [
      { id: "mes1", company: "예시 제조", title: "MES 개발 신입", due: "2026-11-15", by: "나", at: "2026-11-03", fans: [["자람", 1], ["차오름", 0]], me: "지원함", comments: 2 },
      { id: "mes2", company: "예시 자동화", title: "스마트팩토리 SW", due: "2026-11-27", by: "자람", at: "2026-11-10", fans: [["자람", 0]], me: "on", comments: 0 },
    ],
  },
]

// 상세 화면에만 있는 값. 디자인에는 "예시 커머스 · 백엔드 개발자 신입"(be1) 하나만 그려져 있다.
const kst = (date: string) => `${date}T12:00:00+09:00`
const BE1_COMMENTS: Comment[] = [
  { id: "be1-c1", author: member("가람"), createdAt: kst("2026-11-05"), body: [{ text: "코딩 테스트 일정이 공고 아래쪽에 따로 적혀 있어요. 접수 마감 바로 다음 주입니다." }] },
  { id: "be1-c2", author: member("나래"), createdAt: kst("2026-11-09"), body: [{ text: "작년 지원 후기를 찾았어요. " }, { text: "후기 글 보기", href: "#" }] },
  { id: "be1-c3", author: VIEWER, createdAt: kst("2026-11-12"), body: [{ text: "포트폴리오 링크 칸이 필수라서 미리 정리해 두면 좋겠어요." }] },
]
const DETAILS: Record<string, Partial<GroupPostDetail>> = {
  be1: {
    deadlineName: "서류 접수 마감",
    applyAt: "회사 채용 홈페이지",
    sections: [
      { title: "주요 업무", items: ["주문·결제 서버 API 개발과 운영", "배치 작업과 내부 관리 도구 개발"] },
      { title: "필수 요건", items: ["Java 또는 Kotlin으로 서버를 개발해 본 경험", "관계형 DB를 써 본 경험"] },
      { title: "우대 사항", items: ["캐시나 메시지 큐를 써 본 경험", "테스트 코드를 작성해 온 경험"] },
    ],
    keywords: ["Java", "Kotlin", "Spring Boot", "JPA", "Redis"],
    comments: BE1_COMMENTS,
  },
}
/** 상세에서만 보이는 지원 단계("단계·결과까지"를 연 멤버만). */
const PROGRESS: Record<string, Partial<Record<Name, string>>> = { be1: { 나래: "면접 단계" } }

function toDeadline(due: string): Deadline {
  if (due === "always") return { kind: "always" }
  if (due === "unknown") return { kind: "unknown" }
  return { kind: "date", date: due }
}

export interface ExampleGroup {
  id: string
  name: string
  mark: string
  members: Member[]
  leaderId: string
  myDisclosure: Disclosure
  posts: GroupPostDetail[]
}

/** 예시 데이터를 새로 만든다. db.ts가 처음 한 번 불러 메모리에 두고 고친다. */
export function createExampleGroups(): ExampleGroup[] {
  return RAW.map((g) => ({
    id: g.id,
    name: g.name,
    mark: g.mark,
    members: [VIEWER, ...g.members.map(member)],
    leaderId: people[g.leader],
    myDisclosure: g.myDisclosure,
    posts: g.posts.map((p) => ({
      id: p.id,
      groupId: g.id,
      company: p.company,
      title: p.title,
      deadline: toDeadline(p.due),
      postedBy: p.by === "나" ? VIEWER : member(p.by),
      postedAt: p.at,
      interests: p.fans.map(([name, applied]) => ({
        member: member(name),
        applied: applied === 1,
        ...(PROGRESS[p.id]?.[name] ? { progress: PROGRESS[p.id][name] } : {}),
      })),
      me: { interested: p.me !== "off", status: p.me === "on" || p.me === "off" ? null : p.me },
      commentCount: p.comments,
      deadlineName: null,
      applyAt: null,
      sourceUrl: null,
      sections: [],
      keywords: [],
      comments: [],
      ...DETAILS[p.id],
    })),
  }))
}
