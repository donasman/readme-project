import type { ReactNode } from "react"
import { PlusIcon, SearchIcon } from "lucide-react"

import AppHeader from "@/components/arc/blocks/app-header/app-header"
import SegmentedControl from "@/components/arc/segmented-control/segmented-control"
import { useSoundSettings } from "@/components/arc/sound/settings"
import { useTheme } from "@/components/theme-provider"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"

// 확인용 한 페이지(계획 4.9). shadcn 부품과 dsui 부품을 나란히 놓고 7절 항목을 눈으로 본다.

/** 리드미 로고가 정해지기 전까지 쓰는 자리표시 마크. */
function ReadmeMark() {
  return (
    <span
      aria-hidden="true"
      className="grid size-[26px] place-items-center rounded-[8px] bg-primary text-xs font-bold text-primary-foreground"
    >
      리
    </span>
  )
}

const THEME_OPTIONS = [
  { value: "light", label: "라이트" },
  { value: "dark", label: "다크" },
  { value: "system", label: "시스템" },
]

function Section({
  id,
  title,
  children,
}: {
  id: string
  title: string
  children: ReactNode
}) {
  return (
    <section id={id} className="flex scroll-mt-24 flex-col gap-4">
      <h2 className="font-heading text-xl font-bold">{title}</h2>
      {children}
    </section>
  )
}

export function App() {
  const { theme, setTheme } = useTheme()
  const sound = useSoundSettings()

  return (
    <>
      <AppHeader
        brand={{ name: "리드미", href: "/", mark: <ReadmeMark /> }}
        navLabel="주요 메뉴"
        items={[
          { href: "#buttons", label: "버튼", current: true },
          { href: "#inputs", label: "입력" },
          { href: "#dialog", label: "다이얼로그" },
        ]}
        barActions={
          <Button size="sm" variant="outline">
            로그인
          </Button>
        }
        cta={{ href: "#buttons", label: "시작하기" }}
      />

      <main className="mx-auto flex max-w-3xl flex-col gap-12 px-4 pt-8 pb-[60vh]">
        <Section id="theme" title="테마">
          <SegmentedControl
            label="테마"
            options={THEME_OPTIONS}
            value={theme}
            onValueChange={(value) => setTheme(value as typeof theme)}
          />
          <p className="text-sm text-muted-foreground">
            테마 선택: {theme}. 효과음: {sound.enabled ? "켜짐" : "꺼짐"}
            (음량 {sound.volume}).
          </p>
        </Section>

        <Section id="buttons" title="버튼">
          <div className="flex flex-wrap items-center gap-3">
            <Button>기본</Button>
            <Button variant="secondary">보조</Button>
            <Button variant="outline">외곽선</Button>
            <Button variant="ghost">고스트</Button>
            <Button variant="destructive">삭제</Button>
            <Button variant="link">링크</Button>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Button size="xs">xs 24</Button>
            <Button size="sm">sm 36</Button>
            <Button>default 44</Button>
            <Button size="lg">lg 50</Button>
            <Button size="icon-sm" variant="outline" aria-label="검색">
              <SearchIcon />
            </Button>
            <Button size="icon" aria-label="추가">
              <PlusIcon />
            </Button>
            <Button disabled>비활성</Button>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Button size="sm">shadcn sm</Button>
            <SegmentedControl
              label="dsui 세그먼트"
              options={[
                { value: "all", label: "전체" },
                { value: "mine", label: "내 카드" },
                { value: "saved", label: "저장함" },
              ]}
              value="all"
              onValueChange={() => {}}
            />
          </div>
        </Section>

        <Section id="inputs" title="입력">
          <div className="flex max-w-md items-center gap-3">
            <Input placeholder="이메일" type="email" />
            <Button>확인</Button>
          </div>
          <Input
            className="max-w-md"
            placeholder="잘못된 값"
            aria-invalid="true"
          />
        </Section>

        <Section id="dialog" title="다이얼로그">
          <Dialog>
            <DialogTrigger render={<Button variant="outline" />}>
              다이얼로그 열기
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>경험카드 이름</DialogTitle>
                <DialogDescription>
                  shadcn 다이얼로그 안의 입력과 버튼입니다.
                </DialogDescription>
              </DialogHeader>
              <Input placeholder="예: 첫 프로젝트 회고" />
              <DialogFooter>
                <DialogClose render={<Button variant="outline" />}>
                  취소
                </DialogClose>
                <Button>저장</Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </Section>
      </main>
    </>
  )
}

export default App
