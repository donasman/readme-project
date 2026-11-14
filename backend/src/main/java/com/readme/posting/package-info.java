/**
 * 공고 본체와 내 공고(지원 상태의 원본, 나만 보는 메모).
 *
 * <p>테이블: postings, posting_keywords, user_postings. API: /api/me/postings(목록, 상세, 상태, 메모).
 * <p>다른 기능은 {@link com.readme.posting.ApplicationStatus} 로 상태 글자를 만들고,
 * {@link com.readme.posting.UserPostings} 로 내 공고 행을 만든다. 마감과 공고 내용 묶음의 응답 모양
 * ({@link com.readme.posting.DeadlineResponse}, {@link com.readme.posting.PostSection})은 그룹 공고와 함께 쓴다.
 * 근거: docs/리드미_설계_공고.md.
 */
package com.readme.posting;
