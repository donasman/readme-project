// 화면 주소를 만드는 곳. 링크, 이동, redirect는 이 함수로 주소를 만들고 문자열로 직접 적지 않는다.
// 라우트 정의의 path("/groups/:groupId" 같은 틀)는 app/router.tsx와 각 기능의 routes에 둔다.

const seg = encodeURIComponent

export const paths = {
  home: () => "/",
  login: () => "/login",
  signup: () => "/signup",
  signupSent: () => "/signup/sent",
  passwordReset: () => "/password-reset",
  passwordResetSent: () => "/password-reset/sent",
  /** 내 그룹 목록 */
  groups: () => "/groups",
  group: (groupId: string) => `/groups/${seg(groupId)}`,
  groupPosts: (groupId: string) => `/groups/${seg(groupId)}/posts`,
  groupPost: (groupId: string, postId: string) => `/groups/${seg(groupId)}/posts/${seg(postId)}`,
  /** 공고 올리기와 그 다음 칸 확인 */
  groupPostNew: (groupId: string) => `/groups/${seg(groupId)}/posts/new`,
  groupPostCheck: (groupId: string) => `/groups/${seg(groupId)}/posts/new/check`,
  groupMembers: (groupId: string) => `/groups/${seg(groupId)}/members`,
  /** 초대 링크가 여는 주소 */
  invite: (token: string) => `/invite/${seg(token)}`,
  cards: () => "/cards",
  card: (cardId: number) => `/cards/${cardId}`,
  cardEdit: (cardId: number) => `/cards/${cardId}/edit`,
  cardSources: () => "/cards/sources",
  /** 카드 확인. cardId를 주면 그 카드부터 본다 */
  cardReview: (cardId?: number) => (cardId === undefined ? "/cards/review" : `/cards/review?card=${cardId}`),
  devComponents: () => "/dev/components",
}
