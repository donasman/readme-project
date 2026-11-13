import type { RouteObject } from "react-router"

/**
 * 로그인 전 화면들(랜딩, 로그인, 회원가입, 이메일 인증, 비밀번호 재설정, 그룹 초대 수락). 로그인하지 않은 사람도 보는 화면이라,
 * 앱의 뿌리 틀 바로 아래(보는 사람을 읽기 전)에 붙는다. 화면은 그 주소에 처음 들어갈 때 불러온다(lazy).
 * /verify-email, /password-reset/new 는 서버가 보내는 메일의 링크가 여는 주소다(?token=…).
 */
export const authRoutes: RouteObject[] = [
  {
    // 로그인 전 첫 화면. 로그인하지 않은 사람이 /로 오면 여기로 온다
    path: "/welcome",
    lazy: async () => ({ Component: (await import("./pages/landing-page")).LandingPage }),
  },
  {
    path: "/login",
    lazy: async () => ({ Component: (await import("./pages/login-page")).LoginPage }),
  },
  {
    path: "/signup",
    lazy: async () => ({ Component: (await import("./pages/signup-page")).SignupPage }),
  },
  {
    path: "/signup/sent",
    lazy: async () => ({ Component: (await import("./pages/signup-sent-page")).SignupSentPage }),
  },
  {
    path: "/verify-email",
    lazy: async () => {
      const { VerifyEmailPage, verifyEmailLoader } = await import("./pages/verify-email-page")
      return { loader: verifyEmailLoader, Component: VerifyEmailPage }
    },
  },
  {
    path: "/password-reset",
    lazy: async () => ({ Component: (await import("./pages/password-reset-page")).PasswordResetPage }),
  },
  {
    path: "/password-reset/sent",
    lazy: async () => ({ Component: (await import("./pages/password-reset-sent-page")).PasswordResetSentPage }),
  },
  {
    // 초대 링크가 여는 주소. 로그인 전에도 그룹 이름을 보여 주고, 로그인했으면 바로 들어간다.
    path: "/invite/:token",
    lazy: async () => {
      const { InvitePage, inviteLoader } = await import("./pages/invite-page")
      return { loader: inviteLoader, Component: InvitePage }
    },
  },
  {
    path: "/password-reset/new",
    lazy: async () => {
      const { PasswordResetNewPage, passwordResetNewLoader } = await import("./pages/password-reset-new-page")
      return { loader: passwordResetNewLoader, Component: PasswordResetNewPage }
    },
  },
]
