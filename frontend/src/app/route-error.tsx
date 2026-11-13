import { Link, isRouteErrorResponse, useRouteError } from "react-router"

import { Heading } from "@/shared/components/heading"
import { Page } from "@/shared/components/page"
import { textLink } from "@/shared/components/text-link"
import { NotFoundError } from "@/shared/lib/errors"
import { paths } from "@/shared/lib/paths"
import { joinClasses } from "@/shared/lib/utils"

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
