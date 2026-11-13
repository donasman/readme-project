import type { ReactNode } from "react"
import { Link } from "react-router"

import { buttonVariants } from "@/components/ui/button"
import { DISCLOSURE_DESCRIPTIONS, DISCLOSURE_LABELS, DISCLOSURES } from "@/domain/group/disclosure"
import { LogoMark } from "@/shared/components/logo-mark"
import { Panel } from "@/shared/components/panel"
import { CalendarIcon, FileTextIcon, UsersIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"

const FEATURES: { icon: ReactNode; title: string; body: string }[] = [
  {
    icon: <UsersIcon className="size-6" />,
    title: "그룹에서 공고 모으기",
    body: "멤버 누구나 공고를 올립니다. 누가 관심 있는지와 댓글이 공고마다 모이고, 마감이 가까운 순서로 보입니다.",
  },
  {
    icon: <FileTextIcon className="size-6" />,
    title: "경험을 카드로 정리하기",
    body: "이력서나 포트폴리오를 올리면 경험을 상황·과제·행동·결과로 나눈 카드로 뽑습니다. 원문에 없는 숫자는 따로 표시해 확인하게 합니다.",
  },
  {
    icon: <CalendarIcon className="size-6" />,
    title: "지원 상태 챙기기",
    body: "공고마다 어느 단계인지 적어 두고 마감을 한눈에 봅니다. 그룹에는 내가 정한 만큼만 보입니다.",
  },
]

/** 섹션 제목(28px). */
const sectionTitle = "font-heading text-[28px] leading-[1.2] font-bold tracking-display"

/** 랜딩(시안 9차): 로그인하지 않은 사람이 처음 보는 화면. 하는 일 셋과 공개 범위 셋을 알린다. */
export function LandingPage() {
  return (
    <>
      <header className="bg-background">
        <div className="mx-auto flex min-h-18 max-w-[1120px] flex-wrap items-center justify-between gap-x-5 gap-y-2 px-6 py-2">
          <Link to={paths.welcome()} aria-label="리드미 홈" className="inline-flex min-h-control-md flex-none items-center gap-2 text-base font-bold text-foreground">
            <LogoMark size={26} />
            <span>리드미</span>
          </Link>
          <div className="flex items-center gap-2">
            <Link to={paths.login()} className={buttonVariants({ variant: "outline", size: "sm" })}>
              로그인
            </Link>
            <Link to={paths.signup()} className={buttonVariants({ size: "sm" })}>
              회원가입
            </Link>
          </div>
        </div>
      </header>

      <main className="mx-auto flex max-w-[1120px] flex-col gap-18 px-6 pt-14 pb-20">
        <section aria-labelledby="ld-hero" className="flex flex-col items-start gap-6">
          <h1
            id="ld-hero"
            className="flex flex-col items-start gap-2 font-heading text-[clamp(2.25rem,9cqw,4.5rem)] leading-display font-bold tracking-display"
          >
            <span>취업준비생을 위한</span>
            <span className="-ml-1.5 rounded-control bg-highlight px-3.5 py-1 text-highlight-foreground">정보공유·스터디</span>
          </h1>
          <p className="max-w-140 text-lg text-text-secondary">그룹에서 공고를 같이 모으고, 내 경험은 카드로 정리해 공고에 맞춰 봅니다.</p>
          <div className="flex flex-wrap gap-2">
            <Link to={paths.signup()} className={buttonVariants({ size: "lg", className: "px-7" })}>
              시작하기
            </Link>
            <Link to={paths.login()} className={buttonVariants({ variant: "outline", size: "lg", className: "px-7" })}>
              로그인
            </Link>
          </div>
        </section>

        <section aria-labelledby="ld-what" className="flex flex-col gap-6">
          <h2 id="ld-what" className={sectionTitle}>
            리드미에서 하는 일
          </h2>
          <ul className="grid grid-cols-[repeat(auto-fit,minmax(min(300px,100%),1fr))] gap-5">
            {FEATURES.map((f) => (
              <Panel as="li" key={f.title} className="flex flex-col gap-3 px-6 py-7">
                <span aria-hidden="true" className="inline-flex size-12 items-center justify-center rounded-control bg-accent-subtle text-accent-strong">
                  {f.icon}
                </span>
                <h3 className="text-lg font-bold">{f.title}</h3>
                <p className="text-text-secondary">{f.body}</p>
              </Panel>
            ))}
          </ul>
        </section>

        <section aria-labelledby="ld-open" className="flex flex-col gap-5 rounded-surface bg-surface-muted px-6 py-8">
          <div className="flex flex-col gap-2">
            <h2 id="ld-open" className={sectionTitle}>
              같은 공고에 지원해도 편하게
            </h2>
            <p className="max-w-160 text-text-secondary">그룹원은 같은 공고의 경쟁자이기도 합니다. 그래서 지원 상태를 어디까지 보일지 그룹마다 따로 고릅니다.</p>
          </div>
          <ul className="grid grid-cols-[repeat(auto-fit,minmax(min(240px,100%),1fr))] gap-3">
            {DISCLOSURES.map((d) => (
              <li key={d} className="flex flex-col gap-1 rounded-control border border-border bg-surface px-5 py-4">
                <strong className="font-bold">{DISCLOSURE_LABELS[d]}</strong>
                <span className="text-label-sm text-text-secondary">{DISCLOSURE_DESCRIPTIONS[d].replace(/\.$/, "")}</span>
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="ld-start" className="flex flex-wrap items-center justify-between gap-x-6 gap-y-4">
          <h2 id="ld-start" className={sectionTitle}>
            초대 링크를 받았다면 그 링크로 들어오세요
          </h2>
          <Link to={paths.signup()} className={buttonVariants({ size: "lg", className: "px-7" })}>
            회원가입
          </Link>
        </section>
      </main>
    </>
  )
}
