package com.readme.posting;

/**
 * 내 공고에서 공고를 뺐다({@link UserPostings#remove}, 공고 설계 4.2). 뺀 트랜잭션 안에서 바로 받는다.
 * grouppost 가 받아 그 사람이 내 그룹들에서 켜 둔 그 공고의 관심을 끈다. 관심을 켜면 내 공고에 들어가므로, 남겨 두면 둘이 어긋난다.
 * posting 이 grouppost 를 부르면 두 패키지가 서로를 부르게 되어 이벤트로 넘긴다.
 */
public record MyPostingRemoved(long userId, long postingId) {
}
