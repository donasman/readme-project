import { useState, type FormEvent } from "react"
import { Link, useLoaderData, useNavigate } from "react-router"

import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ChoiceInput } from "@/shared/components/choice-input"
import { Dot } from "@/shared/components/dot"
import { FieldError, FormField, noteId } from "@/shared/components/form-field"
import { sectionTitle, subLabel } from "@/shared/components/heading"
import { Notice } from "@/shared/components/notice"
import { Panel } from "@/shared/components/panel"
import { objectParticle } from "@/shared/lib/korean"
import { paths } from "@/shared/lib/paths"

import { getAccount, getWithdrawInfo, withdraw } from "../api"

/** 지워질 것과, 확인을 비밀번호로 할지 이메일로 할지(비밀번호가 없는 계정). */
export async function withdrawLoader() {
  const [info, account] = await Promise.all([getWithdrawInfo(), getAccount()])
  return { info, hasPassword: account.hasPassword }
}

/** 탈퇴(시안 9차): 지워지는 것과 남는 것, 내가 그룹장인 그룹, 비밀번호(없는 계정은 이메일)와 확인 표시. */
export function WithdrawPage() {
  const { info, hasPassword } = useLoaderData<typeof withdrawLoader>()
  const navigate = useNavigate()
  // 비밀번호가 없는 계정(구글로만 가입)은 계정의 이메일로 확인한다(로그인 문서 7절 20번)
  const what = hasPassword ? "비밀번호" : "이메일"
  const [password, setPassword] = useState("")
  const [agreed, setAgreed] = useState(false)
  const [errors, setErrors] = useState<{ password?: string; agreed?: string }>({})
  const gone = [
    "계정과 로그인 정보",
    `경험카드 ${info.cards}장과 올린 자료 ${info.sources}개`,
    `내 공고 ${info.postings}건과 지원 상태`,
    `그룹 ${info.groups}곳의 멤버 자격과 켜 둔 관심`,
  ]

  async function submit(event: FormEvent) {
    event.preventDefault()
    const next = { password: password.trim() ? undefined : `${what}${objectParticle(what)} 넣어 주세요.`, agreed: agreed ? undefined : "확인 표시를 해 주세요." }
    setErrors(next)
    if (next.password || next.agreed) return
    const confirmed = await withdraw(hasPassword ? { password } : { email: password.trim() })
    if (!confirmed) return setErrors({ password: hasPassword ? "비밀번호가 맞지 않습니다." : "계정의 이메일과 맞지 않습니다." })
    // 로그인한 화면들(세션 라우트) 밖으로 나간다. 안에 머물면 보는 사람을 다시 읽지 않는다
    navigate(paths.welcome(), { replace: true })
  }

  return (
    <>
      <Panel aria-labelledby="wd-what" className="flex flex-col gap-5 p-6">
        <div className="flex flex-col gap-1">
          <h2 id="wd-what" className={sectionTitle}>
            탈퇴
          </h2>
          <p className="text-sm text-text-secondary">탈퇴하면 계정과 내 자료가 지워지고 되돌릴 수 없습니다.</p>
        </div>
        <div className="flex flex-col gap-2">
          <h3 className={subLabel}>지워지는 것</h3>
          <ul className="flex flex-col gap-2 text-sm">
            {gone.map((g) => (
              <li key={g} className="flex items-start gap-2.5">
                <Dot tone="danger" className="mt-2" />
                <span>{g}</span>
              </li>
            ))}
          </ul>
        </div>
        <div className="flex flex-col gap-2">
          <h3 className={subLabel}>남는 것</h3>
          <ul className="flex flex-col gap-2 text-sm">
            <li className="flex items-start gap-2.5">
              <Dot tone="muted" className="mt-2" />
              <span>그룹에 올린 공고와 쓴 댓글은 남습니다. 올린 사람과 쓴 사람 자리만 비게 됩니다.</span>
            </li>
          </ul>
        </div>
      </Panel>

      {info.leads.map((g) => (
        <Notice key={g.id} title={`${g.name}의 그룹장입니다`}>
          <span>
            {g.successor
              ? `탈퇴하면 가장 먼저 들어온 멤버인 ${g.successor.name} 님이 그룹장을 이어받습니다. 멤버 ${g.others}명은 그대로 쓸 수 있습니다.`
              : "탈퇴하면 이 그룹은 지워집니다. 나 말고 멤버가 없어서입니다. 그룹에 올린 공고와 댓글도 함께 지워집니다."}
          </span>
        </Notice>
      ))}

      <Panel aria-labelledby="wd-confirm" className="flex flex-col gap-4 p-6">
        <h2 id="wd-confirm" className={sectionTitle}>
          탈퇴 확인
        </h2>
        <form onSubmit={submit} noValidate className="contents">
          <FormField id="wd-password" label={what} note={errors.password && <FieldError id="wd-password">{errors.password}</FieldError>}>
            <Input
              id="wd-password"
              type={hasPassword ? "password" : "email"}
              autoComplete={hasPassword ? "current-password" : "email"}
              value={password}
              aria-invalid={errors.password ? true : undefined}
              aria-describedby={errors.password ? noteId("wd-password") : undefined}
              onChange={(e) => {
                setPassword(e.target.value)
                setErrors((er) => ({ ...er, password: undefined }))
              }}
              className="max-w-90 md:text-base aria-invalid:ring-0"
            />
          </FormField>
          <div className="flex flex-col gap-1">
            <label className="flex min-h-11 cursor-pointer items-start gap-2.5 text-sm">
              <ChoiceInput
                type="checkbox"
                checked={agreed}
                aria-invalid={errors.agreed ? true : undefined}
                aria-describedby={errors.agreed ? noteId("wd-agree") : undefined}
                onChange={(e) => {
                  setAgreed(e.target.checked)
                  setErrors((er) => ({ ...er, agreed: undefined }))
                }}
                className="mt-0.5"
              />
              <span>지워진 자료는 되돌릴 수 없다는 것을 확인했습니다.</span>
            </label>
            {errors.agreed && <FieldError id="wd-agree">{errors.agreed}</FieldError>}
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="submit" variant="danger">
              탈퇴하기
            </Button>
            <Link to={paths.account()} className={buttonVariants({ variant: "outline" })}>
              취소
            </Link>
          </div>
        </form>
      </Panel>
    </>
  )
}
