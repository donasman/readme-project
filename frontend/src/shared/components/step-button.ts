/**
 * 위·아래, 앞·뒤처럼 끝에서 꺼지는 버튼의 흐림. 꺼져도 초점을 잃지 않게 focusableWhenDisabled로 두면 disabled 속성 대신
 * aria-disabled가 붙어 공용 Button의 disabled:opacity-50이 듣지 않으므로 시안의 흐림(0.35)을 여기서 준다.
 */
export const stepButton = "aria-disabled:pointer-events-none aria-disabled:opacity-35"

/** 일하는 동안 꺼지는 버튼(LLM 초안·고르기)의 흐림. 누른 버튼이 꺼져도 초점이 body로 떨어지지 않게 focusableWhenDisabled와 함께 쓴다 */
export const busyButton = "aria-disabled:pointer-events-none aria-disabled:opacity-50"
