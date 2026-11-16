// 백엔드 연결 전 경험카드 예시. 목록 6장은 "리드미 시안 3종"의 "A · 카드 목록", 첫 카드의 STAR·근거·원문은
// 6차 "카드 상세"와 "카드 확인"의 값을 그대로 옮겼다. 회사, 프로젝트, 경험은 모두 지어낸 예시다.
// 화면과 api.ts는 이 파일을 직접 읽지 않는다. 같은 폴더의 db.ts만 읽는다.
import type { CardDetail, CardSource, Star } from "@/features/cards/types"

const EMPTY_STAR: Star = { situation: "", task: "", action: "", result: "" }

type Base = Omit<CardDetail, "star" | "facts" | "visibility" | "sourceId" | "sourceName" | "sourceExcerpt" | "extractionNotes" | "issues">
const card = (base: Base, more: Partial<CardDetail> = {}): CardDetail => ({
  star: EMPTY_STAR,
  facts: [],
  visibility: "PRIVATE",
  sourceId: null,
  sourceName: null,
  sourceExcerpt: null,
  extractionNotes: [],
  issues: [],
  ...base,
  ...more,
})

/** 올린 자료(새 것이 앞). */
export function createExampleSources(): CardSource[] {
  return [
    { id: "1", name: "예시_포트폴리오.pdf", createdAt: "2026-11-12", chars: 12480, cardCount: 2, runningJobId: null },
    { id: "2", name: "예시_이력서.docx", createdAt: "2026-11-10", chars: 3210, cardCount: 2, runningJobId: null },
    { id: "3", name: "붙여넣은 글", createdAt: "2026-11-08", chars: 1840, cardCount: 1, runningJobId: null },
  ]
}

const PORTFOLIO = { sourceId: "1", sourceName: "예시_포트폴리오.pdf" }
const RESUME = { sourceId: "2", sourceName: "예시_이력서.docx" }
const PASTED = { sourceId: "3", sourceName: "붙여넣은 글" }

/** 예시 카드를 새로 만든다. db.ts가 처음 한 번 불러 메모리에 둔다. */
export function createExampleCards(): CardDetail[] {
  return [
    card(
      {
        id: "1",
        title: "주문 조회 API 응답 시간 개선",
        period: "2025-03 ~ 2025-05",
        org: "예시 커머스 사내 프로젝트",
        role: "백엔드 개발 담당(2인 중 1인)",
        tags: ["Java", "Spring Boot", "Redis"],
        summary: "N+1 조회를 제거하고 캐시를 붙여 응답 시간을 줄인 경험",
        usableFor: ["직무역량", "문제해결"],
        verified: false,
      },
      {
        ...PORTFOLIO,
        star: {
          situation: "주문 조회 화면이 느리다는 문의가 이어졌고, 주문 목록 API의 p95 응답 시간이 1.8초였다.",
          task: "화면 구성을 바꾸지 않고 응답 시간을 1초 아래로 줄이는 일을 맡았다.",
          action: "쿼리 로그에서 주문마다 상품을 따로 읽는 N+1을 찾아 fetch join으로 묶었다. 자주 바뀌지 않는 상품 정보는 Redis에 캐시했다.",
          result: "p95 응답 시간이 0.4초로 줄었고 주문 1건당 쿼리가 21회에서 1회가 됐다. 관련 문의도 [확인 필요: 30%] 줄었다.",
        },
        facts: ["p95 응답 시간 1.8초 → 0.4초", "주문 1건당 DB 쿼리 21회 → 1회"],
        sourceExcerpt:
          "주문 목록 조회가 느리다는 문의가 반복됨. p95 1.8초.\n원인: 주문마다 상품 조회(N+1), 주문 1건에 쿼리 21회. fetch join으로 1회로 줄임. 상품 정보 Redis 캐시.\n적용 후 p95 0.4초. 관련 문의 감소.",
        extractionNotes: ["결과의 30%는 자료 원문에 없어 [확인 필요: 30%]로 바꿨습니다."],
        issues: [{ level: "경고", field: "결과", message: "근거 숫자에 없는 숫자가 있습니다: 30%" }],
      }
    ),
    card(
      {
        id: "2",
        title: "배치 작업 재실행 구조 만들기",
        period: "2025-07 ~ 2025-08",
        org: "예시 물류 회사 인턴",
        role: "백엔드 인턴",
        tags: ["Spring Batch", "JPA", "SQL"],
        summary: "실패한 구간만 다시 돌리도록 배치를 나눈 경험",
        usableFor: ["직무역량", "문제해결"],
        verified: true,
      },
      RESUME
    ),
    card(
      {
        id: "3",
        title: "알고리즘 스터디 운영",
        period: "2026 ~ 현재",
        org: "예시 스터디",
        role: "스터디장",
        tags: ["Python"],
        summary: "풀이 공유 순서를 정해 참여가 끊기지 않게 한 경험",
        usableFor: ["협업", "성장"],
        verified: true,
      },
      PASTED
    ),
    card(
      {
        id: "4",
        title: "캡스톤 프로젝트 일정 조율",
        period: "2024-09 ~ 2024-12",
        org: "예시 대학교 캡스톤",
        role: "팀장(4인)",
        tags: ["TypeScript", "Node.js"],
        summary: "겹친 역할을 다시 나누고 작업 순서를 정한 경험",
        usableFor: ["협업", "문제해결"],
        verified: false,
      },
      {
        ...PORTFOLIO,
        star: {
          situation: "4인 팀의 캡스톤 프로젝트에서 담당이 겹쳐 같은 화면을 두 사람이 따로 만들고 있었다.",
          task: "팀장으로서 역할을 다시 나누고 남은 일정을 맞추는 일을 맡았다.",
          action: "기능 목록을 화면 단위로 쪼개 한 사람씩 맡기고, 주마다 작업 순서를 정해 공유했다.",
          result: "겹친 작업이 없어졌고 마감 1주 전에 기능을 모두 합쳤다.",
        },
        sourceExcerpt: "캡스톤(4인) 팀장. 담당이 겹쳐 같은 화면을 두 명이 만들고 있었음.\n화면 단위로 역할을 다시 나누고 주간 작업 순서를 정함. 마감 1주 전 기능 통합.",
        issues: [{ level: "경고", field: "근거 숫자", message: "근거 숫자가 없습니다" }],
      }
    ),
    card({
      id: "5",
      title: "로그인 기능 직접 구현",
      period: "2025-01 ~ 2025-02",
      org: "개인 프로젝트",
      role: "1인 개발",
      tags: ["Java", "Spring Security"],
      summary: "세션 로그인과 소셜 로그인을 한 계정으로 묶은 경험",
      usableFor: ["직무역량", "성장"],
      verified: true,
    }),
    card(
      {
        id: "6",
        title: "사내 API 문서 정리",
        period: "2024-07 ~ 2024-08",
        org: "예시 스타트업 인턴",
        role: "백엔드 인턴",
        tags: ["Kotlin", "Spring Boot"],
        summary: "흩어진 호출 예시를 문서 하나로 모은 경험",
        usableFor: ["지원동기", "성장"],
        verified: true,
      },
      RESUME
    ),
  ]
}

/**
 * 가짜 추출이 새로 올린 자료에서 "뽑는" 카드. 처음 한 번만 목록에 들어가고, 다시 뽑으면 이미 있는 경험으로 건너뛴다.
 * 이미 있는 "로그인 기능 직접 구현"과 같은 경험 하나도 늘 건너뛴다(시안의 "건너뜀 1개").
 */
export function createExtractionPool(): Omit<CardDetail, "id" | "sourceId" | "sourceName">[] {
  const base = { visibility: "PRIVATE" as const, verified: false, extractionNotes: [], issues: [{ level: "경고" as const, field: "근거 숫자", message: "근거 숫자가 없습니다" }] }
  return [
    {
      ...base,
      title: "결제 알림 재시도 구조 만들기",
      period: "2025-09 ~ 2025-10",
      org: "예시 커머스 사내 프로젝트",
      role: "백엔드 개발 담당",
      tags: ["Java", "Spring Boot", "Kafka"],
      summary: "알림 발송이 실패하면 순서대로 다시 보내도록 큐를 나눈 경험",
      usableFor: ["직무역량", "문제해결"],
      star: {
        situation: "결제 완료 알림이 외부 발송 서비스 장애로 가끔 빠졌다.",
        task: "빠진 알림을 사람이 손으로 다시 보내지 않게 하는 일을 맡았다.",
        action: "발송 요청을 큐에 넣고, 실패한 요청은 간격을 늘려 가며 다시 보내는 단계를 따로 두었다.",
        result: "장애가 끝나면 빠진 알림이 순서대로 다시 나가게 됐다.",
      },
      facts: [],
      sourceExcerpt: "결제 알림 누락 문의. 외부 발송 장애 때 빠짐.\n발송 큐 분리, 실패 시 재시도 간격 늘림.",
    },
    {
      ...base,
      title: "사내 스터디 발표 자료 정리",
      period: "2025-04 ~ 2025-06",
      org: "예시 커머스 사내 스터디",
      role: "발표 담당",
      tags: ["문서화", "커뮤니케이션"],
      summary: "발표 자료를 주제별로 모아 다음 사람이 이어 쓰게 한 경험",
      usableFor: ["협업", "성장"],
      star: {
        situation: "사내 스터디 발표 자료가 사람마다 다른 곳에 흩어져 있었다.",
        task: "지난 발표를 찾아보기 쉽게 정리하는 일을 맡았다.",
        action: "주제별 목차를 만들고 발표마다 요약과 질문을 한 쪽으로 붙였다.",
        result: "새로 온 사람이 지난 발표를 보고 다음 주제를 고르게 됐다.",
      },
      facts: [],
      sourceExcerpt: "스터디 발표 자료 흩어져 있음.\n주제별 목차, 발표 요약과 질문 정리.",
    },
    {
      ...base,
      title: "서버 모니터링 대시보드 구성",
      period: "2025-11 ~ 2025-12",
      org: "예시 커머스 사내 프로젝트",
      role: "백엔드 개발 담당",
      tags: ["Grafana", "Prometheus"],
      summary: "장애를 먼저 알 수 있게 응답 시간과 오류 수를 한 화면에 모은 경험",
      usableFor: ["직무역량"],
      star: {
        situation: "장애를 사용자 문의로 먼저 알게 되는 일이 잦았다.",
        task: "응답 시간과 오류를 한눈에 보는 화면을 만드는 일을 맡았다.",
        action: "API별 응답 시간과 오류 수를 모아 대시보드로 묶고, 기준을 넘으면 알림을 보내게 했다.",
        result: "문의가 오기 전에 장애를 알아채게 됐다.",
      },
      facts: [],
      sourceExcerpt: "장애를 문의로 먼저 알게 됨.\nAPI별 응답 시간·오류 수 대시보드, 기준 넘으면 알림.",
    },
    {
      ...base,
      title: "신입 개발자 온보딩 문서 만들기",
      period: "2025-06 ~ 2025-07",
      org: "예시 커머스 사내 프로젝트",
      role: "백엔드 개발 담당",
      tags: ["문서화", "커뮤니케이션"],
      summary: "개발 환경 설정을 한 문서로 모아 첫 주 질문을 줄인 경험",
      usableFor: ["협업", "지원동기"],
      star: {
        situation: "새로 온 개발자가 개발 환경을 맞추는 데 며칠씩 걸렸다.",
        task: "첫 주에 혼자 따라 할 수 있는 문서를 만드는 일을 맡았다.",
        action: "설치 순서와 자주 막히는 곳을 한 문서로 모으고, 새로 온 사람이 직접 따라 하며 고치게 했다.",
        result: "다음에 온 개발자는 첫날 개발 환경을 맞췄다.",
      },
      facts: [],
      sourceExcerpt: "신규 입사자 개발 환경 설정에 며칠 걸림.\n설치 순서·막히는 곳 문서화, 신규 입사자가 직접 고침.",
    },
    {
      ...base,
      title: "상품 검색 정렬 기준 바꾸기",
      period: "2025-10 ~ 2025-11",
      org: "예시 커머스 사내 프로젝트",
      role: "백엔드 개발 담당",
      tags: ["Java", "SQL"],
      summary: "판매량과 재고를 함께 보도록 검색 정렬을 바꾼 경험",
      usableFor: ["직무역량", "문제해결"],
      star: {
        situation: "검색 결과 위쪽에 품절 상품이 자주 보였다.",
        task: "살 수 있는 상품이 먼저 보이게 정렬을 바꾸는 일을 맡았다.",
        action: "정렬 점수에 재고 여부를 넣고, 바꾸기 전후 검색 결과를 기획자와 함께 비교했다.",
        result: "품절 상품이 검색 결과 뒤쪽으로 내려갔다.",
      },
      facts: [],
      sourceExcerpt: "검색 상단에 품절 상품 노출.\n정렬 점수에 재고 여부 반영, 기획자와 전후 비교.",
    },
  ]
}

/** 태그 대표 표기(GET /api/keywords). 다른 표기 → 대표 표기. */
export const KEYWORD_ALIASES: Record<string, string> = {
  k8s: "Kubernetes",
  js: "JavaScript",
  ts: "TypeScript",
  springboot: "Spring Boot",
  스프링부트: "Spring Boot",
  postgres: "PostgreSQL",
  redis: "Redis",
}

/** 대표 표기 목록(사전의 일부). */
export const KEYWORDS = ["Java", "JavaScript", "TypeScript", "Kotlin", "Python", "Spring Boot", "Spring Security", "Spring Batch", "JPA", "SQL", "PostgreSQL", "MySQL", "Redis", "Kafka", "Kubernetes", "Docker", "Node.js", "React", "Grafana", "Prometheus", "문서화", "커뮤니케이션"]
