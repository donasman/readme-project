/**
 * 그룹 공고(그룹 × 공고). 목록, 상세, 관심, 댓글, 올리기, 고치기, 내리기, 날짜 넣기, 홈의 그룹 소식.
 *
 * <p>테이블: group_posts, group_post_interests, group_post_comments.
 * API: /api/groups/{groupId}/posts/**, /api/me/group-activity.
 * <p>멤버 확인은 group 의 GroupAccess, 공고 본체는 posting 의 Postings, 내 공고 행은 posting 의 UserPostings 로 한다.
 * 근거: docs/리드미_설계_공고.md 4.1, 4.5, 5절.
 */
package com.readme.grouppost;
