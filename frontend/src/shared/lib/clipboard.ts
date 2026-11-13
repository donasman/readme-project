/** 글을 클립보드에 넣는다. 브라우저가 막으면(권한, http) false를 돌려준다. 그때는 칸을 골라 직접 복사하게 둔다. */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}
