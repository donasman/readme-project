/**
 * 공고 본체와 내 공고(지원 상태의 원본).
 *
 * <p>테이블: postings, posting_keywords, user_postings. API: /api/me/postings.
 * <p>다른 기능은 {@link com.readme.posting.ApplicationStatus} 로 상태 글자를 만들고,
 * {@link com.readme.posting.UserPostings} 로 내 공고 행을 만든다.
 * 근거: docs/리드미_설계_그룹_공고.md.
 */
package com.readme.posting;
