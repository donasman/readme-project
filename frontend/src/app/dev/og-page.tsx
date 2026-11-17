import { LogoMark } from "@/shared/components/logo-mark"

/**
 * 링크 미리보기 이미지(og:image, 1200×630)의 원본. 개발 서버에서만 열린다. `npm run shots`가 이 화면을 찍어 public/og.png로 둔다.
 * 오른쪽 앱 화면은 같은 스크립트가 먼저 찍은 소개 페이지의 캡처다.
 */
export function DevOgPage() {
  return (
    <div className="relative h-[630px] w-[1200px] overflow-hidden bg-background">
      <div className="absolute top-[72px] left-[72px] flex w-[520px] flex-col gap-8">
        <span className="inline-flex items-center gap-3 text-[30px] font-bold text-foreground">
          <LogoMark size={44} />
          리드미
        </span>
        <h1 className="flex flex-col items-start gap-3 font-heading text-[56px] leading-display font-bold tracking-display">
          <span>취업준비생을 위한</span>
          <span className="rounded-control bg-highlight px-4 py-1 text-highlight-foreground">정보공유·스터디</span>
        </h1>
        <p className="text-[24px] leading-[1.45] text-text-secondary">그룹에서 공고를 같이 모으고, 내 경험은 카드로 정리해 공고에 맞춰 봅니다.</p>
      </div>
      <img
        src="/welcome/app-light.jpg"
        alt=""
        className="absolute top-[72px] left-[648px] w-[880px] max-w-none rounded-panel border border-border shadow-raised"
      />
    </div>
  )
}
