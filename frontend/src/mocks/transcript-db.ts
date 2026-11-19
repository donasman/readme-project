// 가짜 서버의 성적증명서 읽기(POST /api/transcripts/read, 학점 계산기 문서 4절). 어떤 파일이든 시안의 예시 성적(41과목)을 돌려준다.
// 예시는 화면의 "예시 성적증명서로 보기"와 같은 것이라 api.ts가 넘겨준다(가짜 서버는 기능의 값을 불러오지 않는다).
import type { Transcript } from "@/features/settings/types"

// 읽는 중 화면이 보이도록 잠깐 기다린다. 진짜 서버는 수십 초 걸릴 수 있다
const READ_MS = 1000

export async function readTranscript(example: Transcript): Promise<Transcript> {
  await new Promise((resolve) => setTimeout(resolve, READ_MS))
  return example
}
