/**
 * 리드미 로고 마크: 강조색 바탕에 하이라이트 줄이 그어진 문서. 시안(시안 3종의 A 안)의 마크를 그대로 옮겼다.
 * 꾸밈이라 읽지 않는다. 옆의 "리드미" 글자나 링크 이름이 알린다.
 */
export function LogoMark({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" className="flex-none">
      <rect width="32" height="32" rx="10" className="fill-primary" />
      <rect x="8" y="7.5" width="12" height="3" rx="1.5" className="fill-primary-foreground" />
      <rect x="5" y="13" width="22" height="7" rx="2.5" className="fill-highlight" />
      <rect x="8" y="15" width="16" height="3" rx="1.5" className="fill-highlight-foreground" />
      <rect x="8" y="22.5" width="9" height="3" rx="1.5" className="fill-primary-foreground" />
    </svg>
  )
}
