package com.readme.grouppost;

/**
 * 홈의 그룹 소식 한 줄. 프런트의 GroupNews 모양이다.
 *
 * @param text   화면 글자. "새 공고 2건"이나 "나래 님이 예시 공사 공고에 댓글을 남겼습니다"
 * @param postId 공고 하나에 대한 소식(댓글)이면 그 공고. 그룹의 새 공고 수면 null
 */
record GroupNewsResponse(GroupLabel group, String text, Long postId) {

	/** 그룹의 이름과 표시 글자. 프런트의 GroupLabel 모양이다. */
	record GroupLabel(long id, String name, String mark) {
	}

}
