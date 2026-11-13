import { Link, isRouteErrorResponse, useRouteError } from "react-router"

import { NotFoundError } from "@/shared/lib/errors"
import { paths } from "@/shared/lib/paths"

/** 주소가 없거나 그룹·공고를 찾지 못했을 때, 그 밖의 오류일 때 보이는 화면. */
export function RouteError({ notFound = false }: { notFound?: boolean }) {
  const error = useRouteError()
  const missing =
    notFound ||
    error instanceof NotFoundError ||
    (isRouteErrorResponse(error) && error.status === 404)
  const message = missing
    ? error instanceof NotFoundError
      ? error.message
      : "주소에 맞는 화면이 없습니다."
    : "화면을 그리다 문제가 생겼습니다. 다시 시도해 주세요."

  return (
    <main className="mx-auto flex max-w-[1240px] flex-col items-start gap-4 px-4 pt-16 pb-16">
      <h1 className="font-heading text-3xl leading-display font-bold tracking-display">
        {missing ? "찾을 수 없습니다" : "문제가 생겼습니다"}
      </h1>
      <p className="text-sm text-text-secondary">{message}</p>
      <Link to={paths.home()} className="text-sm font-bold text-primary underline underline-offset-4">
        첫 화면으로
      </Link>
    </main>
  )
}
