package com.readme.grouppost;

import java.time.LocalDate;
import java.util.List;

import com.readme.group.MemberResponse;
import com.readme.posting.DeadlineResponse;
import com.readme.posting.PostSection;
import com.readme.posting.PostingAnalysis;

/**
 * 그룹 공고 상세. 목록의 한 줄에 공고 내용과 댓글이 더해진다. 프런트의 GroupPostDetail 모양이다.
 *
 * @param analysis 공고 분석(공고 설계 4.7). 아직 없으면 null. 프런트 타입에는 아직 없다(설계 8절 30번)
 */
record GroupPostDetailResponse(long id, long groupId, String company, String title, DeadlineResponse deadline,
		MemberResponse postedBy, LocalDate postedAt, List<InterestResponse> interests, MyStateResponse me,
		int commentCount, String deadlineName, String applyAt, String sourceUrl, List<PostSection> sections,
		List<String> keywords, PostingAnalysis analysis, List<CommentResponse> comments) {

	static GroupPostDetailResponse of(GroupPostResponse post, PostingDetails details, List<String> keywords,
			List<CommentResponse> comments) {
		return new GroupPostDetailResponse(post.id(), post.groupId(), post.company(), post.title(), post.deadline(),
				post.postedBy(), post.postedAt(), post.interests(), post.me(), post.commentCount(),
				details.deadlineName(), details.applyAt(), post.sourceUrl(), details.sections(), keywords,
				details.analysis(), comments);
	}

	/** 상세에만 있는 공고 본체의 값. */
	record PostingDetails(String deadlineName, String applyAt, List<PostSection> sections, PostingAnalysis analysis) {
	}

}
