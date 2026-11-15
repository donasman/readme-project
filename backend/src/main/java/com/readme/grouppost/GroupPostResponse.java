package com.readme.grouppost;

import java.time.LocalDate;
import java.util.List;

import com.readme.group.MemberResponse;
import com.readme.posting.DeadlineResponse;

/**
 * 그룹 공고 목록의 한 줄. 프런트의 GroupPost 모양이다.
 *
 * @param id        공고 본체(postings)의 id. 주소의 postId 다
 * @param postedBy  이 그룹에 올린 사람. 계정을 지웠으면 null
 * @param postedAt  이 그룹에 올린 날(한국 날짜)
 * @param interests 이 그룹에서 관심을 켠 다른 멤버들. 보는 사람은 빼고 me 로 따로 준다
 * @param sourceUrl 원문 주소. 없으면 null. 목록의 마감일 넣기 창이 원문 링크를 보인다
 */
record GroupPostResponse(long id, long groupId, String company, String title, DeadlineResponse deadline,
		MemberResponse postedBy, LocalDate postedAt, List<InterestResponse> interests, MyStateResponse me,
		int commentCount, String sourceUrl) {
}
