/**
 * 위·아래, 앞·뒤처럼 끝에서 꺼지는 버튼의 흐림. 꺼져도 초점을 잃지 않게 focusableWhenDisabled로 두면 disabled 속성 대신
 * aria-disabled가 붙어 공용 Button의 disabled:opacity-50이 듣지 않으므로 시안의 흐림(0.35)을 여기서 준다.
 */
export const stepButton = "aria-disabled:pointer-events-none aria-disabled:opacity-35"
