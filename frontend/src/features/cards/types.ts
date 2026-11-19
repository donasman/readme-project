// 경험카드의 데이터 모양. 서버의 응답(경험카드 문서 4.1, 4.3) 그대로다. 카드 한 장의 모양은 domain/card/types.ts에 있다.
import type { CardDetail, ExperienceCard, QuestionType } from "@/domain/card/types"

/** 내 카드에 쓰인 태그와 그 태그가 붙은 카드 수(GET /api/cards/tags). */
export interface CardTag {
  name: string
  count: number
}

/** 경험카드 목록 화면의 loader가 받는 것. */
export interface CardsData {
  cards: ExperienceCard[]
  tags: CardTag[]
}

/** 카드를 고칠 때 보내는 칸(PUT /api/cards/{id}). */
export type CardInput = Pick<CardDetail, "experience" | "title" | "period" | "org" | "role" | "summary" | "tags" | "usableFor" | "star" | "facts">

/** 올린 자료. 파일은 저장하지 않고 읽어 낸 글만 둔다(설계 가정 7). */
export interface CardSource {
  id: string
  name: string
  /** 올린 날(YYYY-MM-DD) */
  createdAt: string
  /** 읽어 낸 글자 수 */
  chars: number
  /** 이 자료에서 만든 카드 수. 아직 뽑지 않았으면 null */
  cardCount: number | null
  /** 이 자료에서 뽑는 중인 작업. 화면을 나갔다 와도 이 작업을 이어서 보인다 */
  runningJobId: string | null
}

/** 카드 뽑기 작업(서버가 뒤에서 돌리고 화면이 몇 초마다 묻는다, 설계 가정 8). */
export interface ExtractionJob {
  id: string
  sourceId: string
  totalChunks: number
  doneChunks: number
  /** 지금까지 만든 카드 수 */
  cardsSoFar: number
  /** 뽑는 중(서버의 queued·running), 끝남, 실패(다시 시작하면 마친 조각 다음부터 이어서 뽑는다) */
  status: "RUNNING" | "DONE" | "FAILED"
  /** 끝났을 때: 새로 만든 카드 수와 건너뛴 경험(이미 있는 카드의 제목) */
  newCards: number
  skippedSameAs: string[]
  /** 실패했을 때 보일 이유. 아니면 null */
  error: string | null
}

/** 태그 입력 후보(GET /api/keywords?q=): 다른 표기를 쳐도 대표 표기를 준다. */
export interface KeywordSuggestion {
  label: string
  /** 예: "k8s의 대표 표기" */
  note: string | null
}

/** 문항 유형 6종(설계 문서 3.1의 순서). */
export const QUESTION_TYPES: QuestionType[] = ["지원동기", "직무역량", "협업", "문제해결", "성장", "기타"]
