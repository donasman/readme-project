// 백엔드 연결 전 경험카드 예시. "리드미 시안 3종" 캔버스의 "A · 카드 목록"에 있는 카드 6장을 그대로 옮겼다.
// 회사, 프로젝트, 경험은 모두 지어낸 예시다. 화면과 api.ts는 이 파일을 직접 읽지 않는다. 같은 폴더의 db.ts만 읽는다.
import type { ExperienceCard } from "@/features/cards/types"

/** 예시 카드를 새로 만든다. db.ts가 처음 한 번 불러 메모리에 둔다. */
export function createExampleCards(): ExperienceCard[] {
  return [
    {
      id: 1,
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
      id: 2,
      title: "배치 작업 재실행 구조 만들기",
      period: "2025-07 ~ 2025-08",
      org: "예시 물류 회사 인턴",
      role: "백엔드 인턴",
      tags: ["Spring Batch", "JPA", "SQL"],
      summary: "실패한 구간만 다시 돌리도록 배치를 나눈 경험",
      usableFor: ["직무역량", "문제해결"],
      verified: true,
    },
    {
      id: 3,
      title: "알고리즘 스터디 운영",
      period: "2026 ~ 현재",
      org: "예시 스터디",
      role: "스터디장",
      tags: ["Python"],
      summary: "풀이 공유 순서를 정해 참여가 끊기지 않게 한 경험",
      usableFor: ["협업", "성장"],
      verified: true,
    },
    {
      id: 4,
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
      id: 5,
      title: "로그인 기능 직접 구현",
      period: "2025-01 ~ 2025-02",
      org: "개인 프로젝트",
      role: "1인 개발",
      tags: ["Java", "Spring Security"],
      summary: "세션 로그인과 소셜 로그인을 한 계정으로 묶은 경험",
      usableFor: ["직무역량", "성장"],
      verified: true,
    },
    {
      id: 6,
      title: "사내 API 문서 정리",
      period: "2024-07 ~ 2024-08",
      org: "예시 스타트업 인턴",
      role: "백엔드 인턴",
      tags: ["Kotlin", "Spring Boot"],
      summary: "흩어진 호출 예시를 문서 하나로 모은 경험",
      usableFor: ["지원동기", "성장"],
      verified: true,
    },
  ]
}
