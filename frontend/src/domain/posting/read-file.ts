// 공고 읽어 오기에 올릴 파일을 보내기 전에 거른다. 서버가 받는 형식과 크기(공고 문서 4.6)와 같다.
import { FILE_TOO_LARGE, MAX_FILE_BYTES } from "@/shared/lib/api"
import { matchesAccept } from "@/shared/lib/utils"

const DOCUMENTS = ".pdf,.docx,.hwpx,.hwp"
// 사진은 서버가 Claude로 옮겨 적는다. Claude가 받는 형식과 크기다(HEIC, TIFF는 받지 않는다)
const IMAGES = ".jpg,.jpeg,.png,.gif,.webp"
const MAX_IMAGE_BYTES = 5 * 1024 * 1024

/** 파일 고르기 창의 accept. */
export const READ_FILE_ACCEPT = `${DOCUMENTS},${IMAGES}`

/** 이 파일을 보내지 못하는 이유. 보낼 수 있으면 null. 큰 파일을 다 보낸 뒤 서버가 거절하지 않게 먼저 본다. */
export function readFileProblem(file: { name: string; size: number }): string | null {
  if (!matchesAccept(file.name, READ_FILE_ACCEPT)) return "PDF, DOCX, HWPX, HWP 파일과 사진(JPG, PNG, GIF, WebP)만 올릴 수 있습니다."
  if (matchesAccept(file.name, IMAGES) && file.size > MAX_IMAGE_BYTES) return "사진은 5MB까지 올릴 수 있습니다."
  return file.size > MAX_FILE_BYTES ? FILE_TOO_LARGE : null
}
