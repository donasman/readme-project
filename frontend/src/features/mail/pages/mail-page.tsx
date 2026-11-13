import { Link, useLoaderData, useRevalidator } from "react-router"

import { Button, buttonVariants } from "@/components/ui/button"
import { statusLabel } from "@/domain/posting/my-state"
import { Heading } from "@/shared/components/heading"
import { MySpaceNav } from "@/shared/components/my-space-nav"
import { Panel } from "@/shared/components/panel"
import { Pill } from "@/shared/components/pill"
import { PlaceholderLink } from "@/shared/components/placeholder-link"
import { ArrowRightIcon, MailIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"

import { applyMail, getMailbox, ignoreMail, revertAdded } from "../api"

export function mailLoader() {
  return getMailbox()
}

/** 메일 확인함(시안 9차): 지원 상태를 바꾸자는 메일은 눌러야 반영되고, 새 공고는 바로 넣은 기록만 남는다. */
export function MailPage() {
  const { pending, added } = useLoaderData<typeof mailLoader>()
  const revalidator = useRevalidator()
  const act = async (run: () => Promise<void>) => {
    await run()
    await revalidator.revalidate()
  }

  return (
    <main className="mx-auto flex max-w-[1120px] flex-col gap-6 px-6 pt-10 pb-16">
      <MySpaceNav current="mail" />
      <div className="flex flex-col gap-2">
        <Heading level={1} size="text-3xl">
          메일 확인함
        </Heading>
        <p className="text-text-secondary">내 수신 주소로 온 채용 메일에서 읽어 낸 것입니다. 상태를 바꾸는 것은 여기서 눌러야 반영됩니다.</p>
      </div>

      {pending.length === 0 && added.length === 0 ? (
        <section aria-labelledby="mail-empty" className="flex flex-col items-start gap-4 rounded-panel border-[1.5px] border-dashed border-border-strong px-8 py-10">
          <MailIcon aria-hidden="true" className="size-7 text-text-secondary" />
          <div className="flex flex-col gap-2">
            <h2 id="mail-empty" className="text-lg font-bold">
              아직 들어온 메일이 없습니다
            </h2>
            <p className="text-text-secondary">채용 메일을 내 수신 주소로 자동 전달하면 여기에 모입니다.</p>
          </div>
          {/* 메일 자동화 설정: 설정 화면이 생기면 그리로 간다 */}
          <PlaceholderLink className={buttonVariants()}>메일 자동화 설정하기</PlaceholderLink>
        </section>
      ) : (
        <>
          {pending.length > 0 && (
            <section aria-labelledby="mail-todo" className="flex flex-col gap-3">
              <h2 id="mail-todo" className="text-lg font-bold">
                확인 필요 {pending.length}
              </h2>
              <ul className="flex flex-col gap-4">
                {pending.map((m) => (
                  <Panel as="li" key={m.id} className="flex flex-col gap-4 p-6">
                    <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
                      <div className="flex flex-col gap-1">
                        <span className="text-label-sm text-text-secondary">
                          {m.from} · {m.receivedAt}
                        </span>
                        <span className="font-bold">{m.subject}</span>
                      </div>
                      <Pill tone="highlight">확인 필요</Pill>
                    </div>
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-control bg-surface-muted p-4">
                      <Link to={paths.myPost(m.postingId)} className="flex min-w-0 flex-[1_1_220px] flex-col gap-0.5 text-foreground">
                        <span className="text-label-sm text-text-secondary">{m.company}</span>
                        <span className="font-bold">{m.title}</span>
                      </Link>
                      <div className="flex flex-wrap items-center gap-2 text-sm">
                        <Pill tone="muted">{statusLabel(m.before)}</Pill>
                        <span className="sr-only">에서</span>
                        <ArrowRightIcon aria-hidden="true" className="size-4" />
                        <Pill tone="accent">{statusLabel(m.after)}</Pill>
                        <span className="sr-only">로 바뀝니다</span>
                      </div>
                    </div>
                    <p className="text-label-sm text-text-secondary">근거: "{m.quote}"</p>
                    <div className="flex flex-wrap justify-end gap-2">
                      <Button type="button" variant="outline" onClick={() => void act(() => ignoreMail(m.id))}>
                        무시
                      </Button>
                      <Button type="button" className="px-6" onClick={() => void act(() => applyMail(m.id))}>
                        반영
                      </Button>
                    </div>
                  </Panel>
                ))}
              </ul>
            </section>
          )}

          <section aria-labelledby="mail-auto" className="flex flex-col gap-3">
            <h2 id="mail-auto" className="text-lg font-bold">
              자동으로 넣은 것
            </h2>
            {added.length > 0 ? (
              <Panel as="ul" className="flex flex-col">
                {added.map((a, i) => (
                  <li key={a.id} className={`flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-4 ${i > 0 ? "border-t border-border-subtle" : ""}`}>
                    <div className="flex min-w-0 flex-[1_1_260px] flex-col gap-1">
                      <span className="text-label-sm text-text-secondary">
                        {a.from} · {a.receivedAt} · {a.note}
                      </span>
                      {/* 이 예시 공고는 내 공고 예시 데이터에 없어 링크를 달지 않는다 */}
                      <span className="font-bold">
                        {a.company} · {a.title}
                      </span>
                    </div>
                    <Button type="button" variant="outline" size="sm" onClick={() => void act(() => revertAdded(a.id))}>
                      되돌리기
                    </Button>
                  </li>
                ))}
              </Panel>
            ) : (
              <p className="text-sm text-text-secondary">메일로 넣은 공고가 없습니다.</p>
            )}
            <p className="text-label-sm text-text-secondary">새 공고는 바로 넣고, 지원 상태를 바꾸는 것만 확인을 받습니다. 채용 메일이 아니면 저장하지 않고 버립니다. 메일 원문은 보관하지 않습니다.</p>
          </section>
        </>
      )}
    </main>
  )
}
