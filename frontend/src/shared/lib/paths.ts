// 화면 주소를 만드는 곳. 링크, 이동, redirect는 이 함수로 주소를 만들고 문자열로 직접 적지 않는다.
// 라우트 정의의 path("/groups/:groupId" 같은 틀)는 app/router.tsx와 각 기능의 routes에 둔다.

const seg = encodeURIComponent

export const paths = {
  home: () => "/",
  login: () => "/login",
  group: (groupId: string) => `/groups/${seg(groupId)}`,
  groupPosts: (groupId: string) => `/groups/${seg(groupId)}/posts`,
  groupPost: (groupId: string, postId: string) => `/groups/${seg(groupId)}/posts/${seg(postId)}`,
  cards: () => "/cards",
  devComponents: () => "/dev/components",
}
