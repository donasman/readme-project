import { useState, type FormEvent } from "react"
import { Link, useNavigate } from "react-router"

import { Button } from "@/components/ui/button"
import { BIRTH_GENDER_CONSENT_DETAIL } from "@/domain/session/consent"
import { NAME_MAX_LENGTH } from "@/domain/session/types"
import { ChoiceInput } from "@/shared/components/choice-input"
import { IconNote } from "@/shared/components/notice"
import { seoulToday } from "@/shared/lib/date"
import { paths } from "@/shared/lib/paths"

import { signup } from "../api"
import { AuthField, AuthFooter, AuthFrame, authFooterLink, AuthHeading, authInlineLink, AuthText, OrGoogle } from "../components/auth-parts"
import { PASSWORD_TOO_LONG, passwordTooLong } from "@/domain/session/password"

const GENDERS = [
  ["", "선택 안 함"],
  ["male", "남"],
  ["female", "여"],
] as const

const CONSENT_MISSING = "생년월일이나 성별을 넣으려면 수집·이용에 동의해 주세요."

/**
 * 회원가입(5차 "회원가입"). 가입하면 확인 메일을 보냈다는 화면으로 간다. 이미 있는 이메일이면 그 자리에서 알려 준다.
 * 생년월일·성별과 동의 둘은 선택이다(로그인 문서 7절 26~28번). 생년월일이나 성별을 넣었으면 첫 동의가 있어야 보낸다(서버도 400으로 막는다).
 */
export function SignupPage() {
  const navigate = useNavigate()
  const [taken, setTaken] = useState(false)
  const [nameEmpty, setNameEmpty] = useState(false)
  const [tooLong, setTooLong] = useState(false)
  const [consentMissing, setConsentMissing] = useState(false)
  const [invalid, setInvalid] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const name = String(form.get("name") ?? "").trim()
    const email = String(form.get("email") ?? "").trim()
    const password = String(form.get("password") ?? "")
    const birthDate = String(form.get("birthDate") ?? "") || null
    const gender = (String(form.get("gender") ?? "") || null) as "male" | "female" | null
    const birthGenderConsent = form.get("birthGenderConsent") === "on"
    const eventNoticeConsent = form.get("eventNoticeConsent") === "on"
    // required는 공백만 넣은 이름을 통과시킨다
    if (!name) return setNameEmpty(true)
    if (passwordTooLong(password)) return setTooLong(true)
    if ((birthDate || gender) && !birthGenderConsent) return setConsentMissing(true)
    setInvalid(null)
    setSaving(true)
    try {
      const result = await signup({ name, email, password, birthDate, gender, birthGenderConsent, eventNoticeConsent })
      if (result.ok) navigate(paths.signupSent(), { state: { email } })
      else if (result.error === "EMAIL_TAKEN") setTaken(true)
      else setInvalid(result.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <AuthFrame>
      <AuthHeading title="회원가입">
        <AuthText>이메일로 가입하면 확인 메일을 보냅니다.</AuthText>
      </AuthHeading>

      <form onSubmit={submit} className="flex flex-col gap-4">
        <AuthField
          id="signup-name"
          name="name"
          label="표시 이름"
          autoComplete="nickname"
          placeholder="다온"
          hint="그룹 멤버에게 보이는 이름입니다."
          maxLength={NAME_MAX_LENGTH}
          required
          onChange={() => setNameEmpty(false)}
          error={nameEmpty && "표시 이름을 넣어 주세요."}
        />
        <AuthField
          id="signup-email"
          name="email"
          label="이메일"
          type="email"
          autoComplete="email"
          placeholder="name@example.com"
          required
          onChange={() => setTaken(false)}
          error={
            taken && (
              <>
                이미 가입된 이메일입니다.{" "}
                <Link to={paths.login()} className={authInlineLink}>
                  로그인
                </Link>
                하거나{" "}
                <Link to={paths.passwordReset()} className={authInlineLink}>
                  비밀번호를 재설정
                </Link>
                하세요.
              </>
            )
          }
        />
        <AuthField
          id="signup-password"
          name="password"
          label="비밀번호"
          type="password"
          autoComplete="new-password"
          hint="8자 이상"
          minLength={8}
          required
          onChange={() => setTooLong(false)}
          error={tooLong && PASSWORD_TOO_LONG}
        />
        <AuthField
          id="signup-birth"
          name="birthDate"
          label="생년월일 (선택)"
          type="date"
          min="1900-01-01"
          max={seoulToday()}
          autoComplete="bday"
          onChange={() => setConsentMissing(false)}
        />
        <fieldset className="flex flex-col gap-1">
          <legend className="mb-1 text-sm font-medium">성별 (선택)</legend>
          <div className="flex flex-wrap gap-x-5">
            {GENDERS.map(([value, label]) => (
              <label key={value} className="flex min-h-11 cursor-pointer items-center gap-2 text-sm">
                <ChoiceInput type="radio" name="gender" value={value} defaultChecked={value === ""} onChange={() => setConsentMissing(false)} />
                {label}
              </label>
            ))}
          </div>
        </fieldset>
        <div className="flex flex-col gap-1">
          <label className="flex min-h-11 cursor-pointer items-start gap-2.5 text-sm">
            <ChoiceInput
              type="checkbox"
              name="birthGenderConsent"
              aria-invalid={consentMissing || undefined}
              aria-describedby={consentMissing ? "signup-consent-note" : undefined}
              onChange={() => setConsentMissing(false)}
              className="mt-0.5"
            />
            <span className="flex flex-col gap-1">
              <span className="font-medium">생년월일·성별 수집·이용 동의 (선택)</span>
              <span className="text-label-sm text-text-secondary">{BIRTH_GENDER_CONSENT_DETAIL}</span>
            </span>
          </label>
          {consentMissing && (
            <IconNote id="signup-consent-note" tone="danger">
              {CONSENT_MISSING}
            </IconNote>
          )}
          <label className="flex min-h-11 cursor-pointer items-center gap-2.5 text-sm">
            <ChoiceInput type="checkbox" name="eventNoticeConsent" />
            <span className="font-medium">이벤트·혜택 안내 받기 (선택)</span>
          </label>
        </div>
        {invalid && (
          <div role="alert">
            <IconNote tone="danger">{invalid}</IconNote>
          </div>
        )}
        <Button type="submit" className="mt-1 w-full" disabled={saving}>
          가입하기
        </Button>
      </form>

      <OrGoogle />

      <AuthFooter prompt="이미 계정이 있나요?">
        <Link to={paths.login()} className={authFooterLink}>
          로그인
        </Link>
      </AuthFooter>
    </AuthFrame>
  )
}
