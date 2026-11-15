import { Link, Navigate, isRouteErrorResponse, useRouteError } from "react-router"

import { Heading } from "@/shared/components/heading"
import { Page } from "@/shared/components/page"
import { textLink } from "@/shared/components/text-link"
import { ApiError } from "@/shared/lib/api"
import { NotFoundError } from "@/shared/lib/errors"
import { sentence } from "@/shared/lib/korean"
import { paths } from "@/shared/lib/paths"
import { joinClasses } from "@/shared/lib/utils"

/** 주소가 없거나 그룹·공고를 찾지 못했을 때(서버의 404 포함), 그 밖의 오류일 때 보이는 화면. 로그인이 풀렸으면(서버의 401) 로그인 화면으로 보낸다. */
export function RouteError({ notFound = false }: { notFound?: boolean }) {
  const error = useRouteError()
  if (error instanceof ApiError && error.status === 401) return <Navigate to={paths.login()} replace />
  const missing =
    notFound ||
    error instanceof NotFoundError ||
    (error instanceof ApiError && error.status === 404) ||
    (isRouteErrorResponse(error) && error.status === 404)
  const message = missing
    ? error instanceof NotFoundError || error instanceof ApiError
      ? sentence(error.message)
      : "주소에 맞는 화면이 없습니다."
    : "화면을 그리다 문제가 생겼습니다. 다시 시도해 주세요."

  return (
    <Page className="items-start gap-4 pt-16">
      <Heading level={1} size="text-3xl">
        {missing ? "찾을 수 없습니다" : "문제가 생겼습니다"}
      </Heading>
      <p className="text-sm text-text-secondary">{message}</p>
      <Link to={paths.home()} className={joinClasses(textLink, "text-sm underline-offset-4")}>
        첫 화면으로
      </Link>
    </Page>
  )
}
