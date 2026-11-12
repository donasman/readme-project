import { useLoaderData, type LoaderFunctionArgs, type ShouldRevalidateFunctionArgs } from "react-router"

import { getGroupPosts } from "@/features/group-posts/api"

export function groupPostsLoader({ params }: LoaderFunctionArgs) {
  return getGroupPosts(params.groupId ?? "")
}

/** 검색어·필터처럼 주소의 ? 뒤만 바뀔 때는 다시 읽지 않는다. */
export function shouldRevalidateGroupPosts({ currentUrl, nextUrl, defaultShouldRevalidate }: ShouldRevalidateFunctionArgs) {
  if (currentUrl.pathname === nextUrl.pathname && currentUrl.search !== nextUrl.search) return false
  return defaultShouldRevalidate
}

/** 그룹 공고 목록. */
export function GroupPostsPage() {
  const data = useLoaderData<typeof groupPostsLoader>()
  return (
    <main className="mx-auto max-w-[1240px] px-4 pt-10 pb-16">
      <h1 className="font-heading text-5xl font-bold">공고</h1>
      <p className="text-sm text-text-secondary">
        {data.group.name} · 공고 {data.posts.length}개
      </p>
    </main>
  )
}
