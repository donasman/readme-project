import { useState, type FormEvent } from "react"
import { Link, useNavigate } from "react-router"

import { Button } from "@/components/ui/button"
import { NAME_MAX_LENGTH } from "@/domain/session/types"
import { paths } from "@/shared/lib/paths"

import { signup } from "../api"
import { AuthField, AuthFooter, AuthFrame, authFooterLink, AuthHeading, AuthText, OrGoogle } from "../components/auth-parts"

const inlineLink = "font-bold text-primary hover:text-accent-strong"

/** 회원가입(5차 "회원가입"). 가입하면 확인 메일을 보냈다는 화면으로 간다. 이미 있는 이메일이면 그 자리에서 알려 준다. */
export function SignupPage() {
  const navigate = useNavigate()
  const [taken, setTaken] = useState(false)
  const [nameEmpty, setNameEmpty] = useState(false)
  const [saving, setSaving] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const name = String(form.get("name") ?? "").trim()
    const email = String(form.get("email") ?? "").trim()
    // required는 공백만 넣은 이름을 통과시킨다
    if (!name) return setNameEmpty(true)
    setSaving(true)
    try {
      const result = await signup({ name, email, password: String(form.get("password") ?? "") })
      if (result.ok) navigate(paths.signupSent(), { state: { email } })
      else setTaken(true)
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
                <Link to={paths.login()} className={inlineLink}>
                  로그인
                </Link>
                하거나{" "}
                <Link to={paths.passwordReset()} className={inlineLink}>
                  비밀번호를 재설정
                </Link>
                하세요.
              </>
            )
          }
        />
        <AuthField id="signup-password" name="password" label="비밀번호" type="password" autoComplete="new-password" hint="8자 이상" minLength={8} required />
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
