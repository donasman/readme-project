// 공고 읽어 오기(POST /api/postings/read, 공고 문서 4.6). 그룹에 올리기와 그룹 없이 내 공고에 넣기가 함께 쓴다.
import * as db from "@/mocks/db"
import { isApiError, mockServer, request, tooLargeMessage } from "@/shared/lib/api"
import { sentence } from "@/shared/lib/korean"

import type { DraftField, DuplicatePost, PostDraft, PostReadInput, ReadPostResult } from "./types"

/** 읽어 오기의 응답(공고 문서 4.6). 못 읽은 칸은 ""이고, 그 그룹에 같은 공고가 있으면 duplicate가 붙는다. 키워드는 사전의 대표 표기다. */
interface ReadResponse extends Omit<PostDraft, "unread"> {
  unread: string[]
  duplicate?: Omit<DuplicatePost, "id"> & { postId: string }
}

/** 칸 확인 화면이 아는 칸. 서버의 unread에는 화면에 없는 deadlineName도 늘 들어 있다(공고 문서 8절 16번). */
const DRAFT_FIELDS: readonly string[] = ["company", "title", "deadline", "sourceUrl", "applyAt"] satisfies DraftField[]

/**
 * 읽어 오기의 본문. 그룹 id를 주면 서버가 그 그룹에 같은 공고가 있는지 함께 본다(파일은 멤버인지만 본다).
 * 내 공고 넣기는 그룹 id 없이 부른다. 서버의 id는 숫자다.
 */
function readBody(groupId: string | null, input: PostReadInput) {
  const group = groupId === null ? {} : { groupId: Number(groupId) }
  if (input.kind === "url") return { url: input.url, ...group }
  if (input.kind === "text") return { text: input.text, sourceUrl: input.sourceUrl, ...group }
  const form = new FormData()
  form.append("file", input.file)
  if (groupId !== null) form.append("groupId", groupId)
  return form
}

/**
 * 공고를 읽어 칸을 채운다: 링크, 붙여넣은 본문, 파일(PDF, DOCX, HWPX, HWP, 사진. 받는 형식은 read-file.ts).
 * 그 그룹에 이미 있으면 그 공고를, 읽지 못하면 이유를 돌려준다. 그룹 id가 null이면(내 공고 넣기) 같은 공고를 보지 않는다.
 */
export async function readPosting(groupId: string | null, input: PostReadInput): Promise<ReadPostResult> {
  if (mockServer) return db.readPosting(groupId, input.kind === "file" ? { kind: "file", name: input.file.name } : input)
  try {
    const { duplicate, unread, ...draft } = await request<ReadResponse>("POST", "/api/postings/read", readBody(groupId, input))
    if (duplicate) {
      const { postId, ...post } = duplicate
      return { status: "duplicate", post: { id: postId, ...post } }
    }
    return { status: "ok", draft: { ...draft, unread: unread.filter((f): f is DraftField => DRAFT_FIELDS.includes(f)) } }
  } catch (error) {
    const tooLarge = tooLargeMessage(error)
    if (tooLarge) return { status: "failed", message: tooLarge }
    // 서버의 글에 이유와 붙여넣기 안내가 있다
    if (isApiError(error, "FETCH_BLOCKED", "FETCH_FAILED", "FILE_UNSUPPORTED_TYPE", "FILE_UNREADABLE", "FILE_NO_TEXT")) return { status: "failed", message: sentence(error.message) }
    throw error
  }
}
