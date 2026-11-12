import { useLoaderData, type LoaderFunctionArgs } from "react-router"

import { getGroupPost } from "@/features/group-posts/api"

export function groupPostLoader({ params }: LoaderFunctionArgs) {
  return getGroupPost(params.groupId ?? "", params.postId ?? "")
}

/** 그룹 공고 상세. */
export function GroupPostPage() {
  const data = useLoaderData<typeof groupPostLoader>()
  return (
    <main className="mx-auto max-w-[1240px] px-4 pt-6 pb-16">
      <h1 className="font-heading text-3xl font-bold">{data.post.title}</h1>
      <p className="text-sm text-text-secondary">{data.post.company}</p>
    </main>
  )
}
