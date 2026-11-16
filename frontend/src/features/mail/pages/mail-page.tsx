import { useState } from "react"
import { Link, useLoaderData, useRevalidator } from "react-router"

import { Button, buttonVariants } from "@/components/ui/button"
import { statusLabel } from "@/domain/posting/my-state"
import { PostingLink } from "@/domain/posting/posting-link"
import { Heading, sectionTitle } from "@/shared/components/heading"
import { MySpaceNav } from "@/shared/components/my-space-nav"
import { Page } from "@/shared/components/page"
import { Panel } from "@/shared/components/panel"
import { Pill } from "@/shared/components/pill"
import { ArrowRightIcon, MailIcon } from "@/shared/icons"
import { paths } from "@/shared/lib/paths"
import { docTitle } from "@/shared/lib/utils"

import { applyMail, getMailbox, ignoreMail, revertAdded } from "../api"

export function mailLoader() {
  return getMailbox()
}

/** 메일 확인함(시안 9차): 지원 상태를 바꾸자는 메일은 눌러야 반영되고, 새 공고는 바로 넣은 기록만 남는다. */
export function MailPage() {
  const { pending, added } = useLoaderData<typeof mailLoader>()
  const revalidator = useRevalidator()
  // 누른 메일은 목록에서 빠지므로 한 일을 화면 읽기에 알린다
  const [done, setDone] = useState("")
  const act = async (run: () => Promise<void>, message: string) => {
    setDone("")
    await run()
    await revalidator.revalidate()
    setDone(message)
  }

  return (
    <Page narrow className="gap-6 pt-10">
      <title>{docTitle("메일 확인함")}</title>
      <MySpaceNav current="mail" />
      <p role="status" className="sr-only">
        {done}
      </p>
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
            <h2 id="mail-empty" className={sectionTitle}>
              아직 들어온 메일이 없습니다
            </h2>
            <p className="text-text-secondary">채용 메일을 내 수신 주소로 자동 전달하면 여기에 모입니다.</p>
          </div>
          <Link to={paths.mailSettings()} className={buttonVariants()}>
            메일 자동화 설정하기
          </Link>
        </section>
      ) : (
        <>
          {pending.length > 0 && (
            <section aria-labelledby="mail-todo" className="flex flex-col gap-3">
              <h2 id="mail-todo" className={sectionTitle}>
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
                      <PostingLink id={m.postingId} company={m.company} title={m.title} className="flex-[1_1_220px]" />
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
                      <Button type="button" variant="outline" onClick={() => void act(() => ignoreMail(m.id), `${m.company} 메일을 무시했습니다.`)}>
                        무시
                      </Button>
                      <Button type="button" className="px-6" onClick={() => void act(() => applyMail(m.id), `${m.company} 메일을 반영했습니다.`)}>
                        반영
                      </Button>
                    </div>
                  </Panel>
                ))}
              </ul>
            </section>
          )}

          <section aria-labelledby="mail-auto" className="flex flex-col gap-3">
            <h2 id="mail-auto" className={sectionTitle}>
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
                    <Button type="button" variant="outline" size="sm" onClick={() => void act(() => revertAdded(a.id), `${a.company} 공고를 내 공고에서 뺐습니다.`)}>
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
    </Page>
  )
}
