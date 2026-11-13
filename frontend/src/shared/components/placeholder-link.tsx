import type { ComponentProps } from "react"

/**
 * 아직 갈 화면이 없는 링크의 자리. 모양은 링크 그대로 두고 누르면 아무 일도 하지 않는다.
 * 화면이 생기면 react-router 의 Link 로 바꾼다. data-placeholder 로 남은 자리를 찾을 수 있다.
 */
export function PlaceholderLink({ onClick, ...props }: ComponentProps<"a">) {
  return (
    <a
      href="#"
      data-placeholder=""
      onClick={(event) => {
        event.preventDefault()
        onClick?.(event)
      }}
      {...props}
    />
  )
}
