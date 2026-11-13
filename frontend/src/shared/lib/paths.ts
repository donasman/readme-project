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
  group: (groupId: string) => `/groups/${seg(groupId)}`,
  groupPosts: (groupId: string) => `/groups/${seg(groupId)}/posts`,
  groupPost: (groupId: string, postId: string) => `/groups/${seg(groupId)}/posts/${seg(postId)}`,
  cards: () => "/cards",
  card: (cardId: number) => `/cards/${cardId}`,
  cardEdit: (cardId: number) => `/cards/${cardId}/edit`,
  cardSources: () => "/cards/sources",
  /** 카드 확인. cardId를 주면 그 카드부터 본다 */
  cardReview: (cardId?: number) => (cardId === undefined ? "/cards/review" : `/cards/review?card=${cardId}`),
  devComponents: () => "/dev/components",
}
