/**
 * 공고 본체와 내 공고(지원 상태의 원본, 나만 보는 메모).
 *
 * <p>테이블: postings, posting_keywords, user_postings. API: /api/me/postings(목록, 넣기, 상세, 상태, 메모).
 * <p>다른 기능은 {@link com.readme.posting.ApplicationStatus} 로 상태 글자를 만들고,
 * {@link com.readme.posting.UserPostings} 로 내 공고 행을 만든다.
 * 올리기 본문({@link com.readme.posting.UploadRequest}, {@link com.readme.posting.DeadlineInput})은 그룹에 올리기와
 * 내 공고 넣기가 함께 쓴다. 올리기의 키워드는 keyword 의 KeywordDictionary 로 대표 표기로 바꾼다. 마감과 공고 내용 묶음의 응답 모양
 * ({@link com.readme.posting.DeadlineResponse}, {@link com.readme.posting.PostSection})은 그룹 공고와 함께 쓴다.
 * <p>공고 분석(설계 4.7): 공고를 올리거나 넣으면({@link com.readme.posting.Postings#findOrCreate}) 커밋 뒤 PostingAnalyses 가
 * 다른 스레드에서 llm 의 Llm 으로 공고마다 한 번 분석해 postings.analysis 에 둔다. 보낼 글과 답의 검사는 PostingAnalysisRules
 * (jobfit analyze.py)다. 두 상세가 {@link com.readme.posting.PostingAnalysis} 로 싣는다.
 * 근거: docs/리드미_설계_공고.md.
 */
package com.readme.posting;
