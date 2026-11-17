// 화면 주소를 만드는 곳. 링크, 이동, redirect는 이 함수로 주소를 만들고 문자열로 직접 적지 않는다.
// 라우트 정의의 path("/groups/:groupId" 같은 틀)는 app/router.tsx와 각 기능의 routes에 둔다.

const seg = encodeURIComponent

export const paths = {
  /** 홈(그룹이 없으면 내 그룹으로 보낸다) */
  home: () => "/",
  /** 로그인 전 첫 화면(랜딩) */
  welcome: () => "/welcome",
  myPosts: () => "/my-posts",
  myPost: (postingId: string) => `/my-posts/${seg(postingId)}`,
  /** 그룹 없이 내 공고에 넣기와 그 다음 칸 확인 */
  myPostNew: () => "/my-posts/new",
  myPostCheck: () => "/my-posts/new/check",
  /** 지원 현황. postingId를 주면 그 공고의 상태 바꾸기를 연다 */
  board: (postingId?: string) => (postingId === undefined ? "/board" : `/board?posting=${seg(postingId)}`),
  calendar: () => "/calendar",
  /** 설정: 내 정보, 메일 자동화, 탈퇴 */
  account: () => "/settings/account",
  /** 카드 뽑기 결과에서 오면 자료에서 채운 값을 확인하라는 안내를 보인다 */
  profile: (fromSource = false) => (fromSource ? "/settings/profile?from=source" : "/settings/profile"),
  mailSettings: () => "/settings/mail",
  withdraw: () => "/settings/withdraw",
  mail: () => "/mail",
  login: () => "/login",
  signup: () => "/signup",
  signupSent: () => "/signup/sent",
  passwordReset: () => "/password-reset",
  passwordResetSent: () => "/password-reset/sent",
  /** 내 그룹 목록 */
  groups: () => "/groups",
  groupPosts: (groupId: string) => `/groups/${seg(groupId)}/posts`,
  groupPost: (groupId: string, postId: string) => `/groups/${seg(groupId)}/posts/${seg(postId)}`,
  /** 공고 올리기와 그 다음 칸 확인 */
  groupPostNew: (groupId: string) => `/groups/${seg(groupId)}/posts/new`,
  groupPostCheck: (groupId: string) => `/groups/${seg(groupId)}/posts/new/check`,
  groupMembers: (groupId: string) => `/groups/${seg(groupId)}/members`,
  /** 초대 링크가 여는 주소 */
  invite: (token: string) => `/invite/${seg(token)}`,
  cards: () => "/cards",
  card: (cardId: string) => `/cards/${cardId}`,
  /** 직접 만들기. 경험 이름을 주면 그 경험의 새 장면으로 연다(경험카드 문서 4.8) */
  cardNew: (experience?: string) => (experience ? `/cards/new?${new URLSearchParams({ experience })}` : "/cards/new"),
  cardEdit: (cardId: string) => `/cards/${cardId}/edit`,
  cardSources: () => "/cards/sources",
  /** 카드 확인. cardId를 주면 그 카드부터 본다 */
  cardReview: (cardId?: string) => (cardId === undefined ? "/cards/review" : `/cards/review?card=${cardId}`),
}
