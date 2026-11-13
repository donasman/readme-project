package com.readme.grouppost;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.readme.common.error.ApiException;
import com.readme.group.GroupAccess;
import com.readme.posting.UserPostings;

/**
 * 그룹 공고를 바꾸는 일. 모두 "그 그룹의 멤버인가", "그 그룹에 있는 공고인가"를 먼저 본다.
 */
@Service
class GroupPostService {

	private final GroupAccess access;

	private final GroupPostQueries queries;

	private final GroupPostInterestRepository interests;

	private final GroupPostCommentRepository comments;

	private final UserPostings userPostings;

	private final Clock clock;

	GroupPostService(GroupAccess access, GroupPostQueries queries, GroupPostInterestRepository interests,
			GroupPostCommentRepository comments, UserPostings userPostings, Clock clock) {
		this.access = access;
		this.queries = queries;
		this.interests = interests;
		this.comments = comments;
		this.userPostings = userPostings;
		this.clock = clock;
	}

	/**
	 * 이 그룹에서 이 공고의 관심을 켜고 끈다. 켤 때 내 공고 행이 없으면 만든다. 끌 때 내 공고 행은 남긴다(설계 가정 10).
	 * 같은 요청을 두 번 보내도 결과가 같다.
	 */
	@Transactional
	MyStateResponse setInterest(long groupId, long postingId, long userId, boolean interested) {
		requireVisiblePost(groupId, postingId, userId);
		if (interested) {
			this.interests.insertIfAbsent(groupId, postingId, userId);
			this.userPostings.addIfAbsent(userId, postingId);
		}
		else {
			this.interests.deleteIfPresent(groupId, postingId, userId);
		}
		return this.queries.myState(groupId, postingId, userId);
	}

	/** 이 그룹의 이 공고에 댓글을 단다. 본문은 앞뒤 공백만 지우고 글자 그대로 저장한다. */
	@Transactional
	CommentResponse addComment(long groupId, long postingId, long userId, String text) {
		requireVisiblePost(groupId, postingId, userId);
		GroupPostComment comment = this.comments
			.save(new GroupPostComment(groupId, postingId, userId, text.strip(), this.clock.instant()));
		return this.queries.comment(comment.getId());
	}

	private void requireVisiblePost(long groupId, long postingId, long userId) {
		this.access.requireMember(groupId, userId);
		if (!this.queries.isVisible(groupId, postingId)) {
			throw new ApiException(GroupPostErrorCode.POST_NOT_FOUND);
		}
	}

}
