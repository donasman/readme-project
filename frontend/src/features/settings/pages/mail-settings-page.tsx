import { useState, type FormEvent } from "react"
import { Link, useLoaderData, useRevalidator } from "react-router"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Dot } from "@/shared/components/dot"
import { FieldError } from "@/shared/components/form-field"
import { sectionTitle } from "@/shared/components/heading"
import { Panel } from "@/shared/components/panel"
import { CheckIcon, CopyIcon } from "@/shared/icons"
import { copyText } from "@/shared/lib/clipboard"
import { paths } from "@/shared/lib/paths"
import { docTitle } from "@/shared/lib/utils"

import { addSender, getMailSettings, removeSender, renewInboundAddress } from "../api"

export function mailSettingsLoader() {
  return getMailSettings()
}

const STEPS = [
  "메일 서비스의 전달 설정에 위 수신 주소를 넣습니다. Gmail은 확인 코드를 보내는데, 그 메일은 메일 확인함에 들어옵니다.",
  '채용 메일만 넘어오도록 필터를 만듭니다. 예: 제목이나 본문에 "채용", "지원", "전형", "면접"이 든 메일.',
  '필터의 동작을 "전달"로 두고 위 주소를 고릅니다.',
]

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** 메일 자동화(시안 9차): 내 수신 주소, 보내는 주소, 자동 전달 설정 안내, 메일을 다루는 규칙. */
export function MailSettingsPage() {
  const { inboundAddress, senders } = useLoaderData<typeof mailSettingsLoader>()
  const revalidator = useRevalidator()
  const [copied, setCopied] = useState(false)
  const [renewed, setRenewed] = useState(false)
  const [email, setEmail] = useState("")
  const [error, setError] = useState<string | null>(null)

  async function add(event: FormEvent) {
    event.preventDefault()
    if (!EMAIL.test(email.trim())) return setError("메일 주소를 넣어 주세요. 예: name@example.com")
    await addSender(email)
    setEmail("")
    await revalidator.revalidate()
  }

  return (
    <>
      <title>{docTitle("메일 자동화", "설정")}</title>
      <Panel aria-labelledby="ma-addr" className="flex flex-col gap-4 p-6">
        <div className="flex flex-col gap-1">
          <h2 id="ma-addr" className={sectionTitle}>
            내 수신 주소
          </h2>
          <p className="text-sm text-text-secondary">채용 메일을 이 주소로 보내면 내 공고와 지원 상태에 반영할 것을 읽어 냅니다.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <label htmlFor="ma-in" className="sr-only">
            수신 주소
          </label>
          <Input id="ma-in" readOnly value={inboundAddress} onFocus={(e) => e.target.select()} className="w-auto min-w-0 flex-[1_1_260px] text-sm md:text-sm" />
          <Button
            type="button"
            variant="outline"
            onClick={async () => setCopied(await copyText(inboundAddress))}
          >
            {copied ? <CheckIcon data-icon="inline-start" aria-hidden="true" /> : <CopyIcon data-icon="inline-start" aria-hidden="true" />}
            {copied ? "복사함" : "복사"}
          </Button>
        </div>
        <p className="text-label-sm text-text-secondary">
          {renewed
            ? "새 주소를 만들었습니다. 메일 서비스의 전달 설정에 있는 주소도 바꿔 주세요."
            : "남이 추측할 수 없게 만든 주소입니다. 다른 사람에게 알려 주지 마세요. 새어 나갔다면 새로 만드세요."}
        </p>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="self-start"
          onClick={async () => {
            // 두 번째 새 주소도 다시 알리도록 먼저 비운다
            setRenewed(false)
            await renewInboundAddress()
            setRenewed(true)
            setCopied(false)
            await revalidator.revalidate()
          }}
        >
          주소 새로 만들기
        </Button>
        <p role="status" className="sr-only">
          {copied ? "수신 주소를 복사했습니다." : renewed ? "새 주소를 만들었습니다." : ""}
        </p>
      </Panel>

      <Panel aria-labelledby="ma-from" className="flex flex-col gap-4 p-6">
        <div className="flex flex-col gap-1">
          <h2 id="ma-from" className={sectionTitle}>
            보내는 주소
          </h2>
          <p className="text-sm text-text-secondary">여기 적힌 주소에서 온 메일만 받습니다.</p>
        </div>
        {senders.length > 0 ? (
          <ul className="flex flex-col">
            {senders.map((s) => (
              <li key={s} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-t border-border-subtle py-4">
                <span>{s}</span>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  aria-label={`${s} 지우기`}
                  onClick={async () => {
                    await removeSender(s)
                    await revalidator.revalidate()
                  }}
                >
                  지우기
                </Button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-text-secondary">아직 적힌 주소가 없어 어떤 메일도 받지 않습니다.</p>
        )}
        <form onSubmit={add} noValidate className="flex flex-col gap-1.5">
          <div className="flex flex-wrap gap-2">
            <label htmlFor="ma-add" className="sr-only">
              보내는 주소 더하기
            </label>
            <Input
              id="ma-add"
              type="email"
              placeholder="name@example.com"
              value={email}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? "ma-add-note" : undefined}
              onChange={(e) => {
                setEmail(e.target.value)
                setError(null)
              }}
              className="w-auto min-w-0 flex-[1_1_220px] md:text-base aria-invalid:ring-0"
            />
            <Button type="submit" variant="outline">
              더하기
            </Button>
          </div>
          {error && <FieldError id="ma-add">{error}</FieldError>}
        </form>
      </Panel>

      <Panel aria-labelledby="ma-how" className="flex flex-col gap-4 p-6">
        <h2 id="ma-how" className={sectionTitle}>
          자동 전달 설정하기
        </h2>
        <ol className="flex flex-col gap-4">
          {STEPS.map((step, i) => (
            <li key={step} className="flex items-start gap-3">
              <span aria-hidden="true" className="grid size-7 flex-none place-items-center rounded-full bg-accent-subtle text-label-sm font-bold text-accent-strong">
                {i + 1}
              </span>
              <span>{step}</span>
            </li>
          ))}
        </ol>
      </Panel>

      <Panel aria-labelledby="ma-rule" className="flex flex-col gap-4 p-6">
        <h2 id="ma-rule" className={sectionTitle}>
          들어온 메일은 이렇게 다룹니다
        </h2>
        <ul className="flex flex-col gap-2.5 text-sm">
          <li className="flex items-start gap-2.5">
            <Dot tone="accent" className="mt-2" />
            <span>새 공고는 내 공고에 바로 넣습니다.</span>
          </li>
          <li className="flex items-start gap-2.5">
            <Dot tone="accent" className="mt-2" />
            <span>
              합격·불합격처럼 지원 상태를 바꾸는 것은{" "}
              <Link to={paths.mail()} className="font-bold whitespace-nowrap text-primary">
                메일 확인함
              </Link>
              에서 눌러야 반영됩니다.
            </span>
          </li>
          <li className="flex items-start gap-2.5">
            <Dot tone="accent" className="mt-2" />
            <span>채용 메일이 아니면 저장하지 않고 버립니다.</span>
          </li>
          <li className="flex items-start gap-2.5">
            <Dot tone="accent" className="mt-2" />
            <span>메일 원문은 보관하지 않습니다. 읽어 낸 칸만 둡니다.</span>
          </li>
        </ul>
      </Panel>
    </>
  )
}
