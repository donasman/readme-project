// 서버의 비밀번호 규칙(로그인 문서 4.1): 8자 이상, UTF-8로 72바이트 이하. 8자는 칸의 minLength가 본다.

/** 서버가 받지 않는 긴 비밀번호인지(영문 72자, 한글 24자를 넘음). 비밀번호 해시(BCrypt)가 72바이트까지만 쓴다. */
export const passwordTooLong = (password: string) => new TextEncoder().encode(password).length > 72

/** 긴 비밀번호의 칸 아래 글. */
export const PASSWORD_TOO_LONG = "비밀번호가 너무 깁니다. 영문은 72자, 한글은 24자까지 쓸 수 있습니다."
