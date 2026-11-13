package com.readme.grouppost;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.readme.common.error.ApiException;
import com.readme.common.error.CommonErrorCode;
import com.readme.group.GroupAccess;
import com.readme.group.GroupMembership;
import com.readme.posting.PostingDraft;
import com.readme.posting.Postings;
import com.readme.posting.UserPostings;

/**
 * 그룹 공고를 바꾸는 일. 모두 "그 그룹의 멤버인가"를 먼저 보고, 올리기 말고는 "그 그룹에 있는 공고인가"도 본다.
 *
 * <p>JPA 엔티티로 바꾼 값은 커밋 전에는 JdbcClient 조회에 보이지 않는다. 그래서 응답에 실을 것은
 * 이 클래스의 트랜잭션이 끝난 뒤 컨트롤러가 GroupPostQueries 로 읽는다(관심처럼 바로 실행되는 쓰기는 여기서 읽는다).
 */
@Service
class GroupPostService {

	private final GroupAccess access;

	private final GroupPostQueries queries;

	private final GroupPostRepository groupPosts;

	private final GroupPostInterestRepository interests;

	private final GroupPostCommentRepository comments;

	private final Postings postings;

	private final UserPostings userPostings;

	private final Clock clock;

	GroupPostService(GroupAccess access, GroupPostQueries queries, GroupPostRepository groupPosts,
			GroupPostInterestRepository interests, GroupPostCommentRepository comments, Postings postings,
			UserPostings userPostings, Clock clock) {
		this.access = access;
		this.queries = queries;
		this.groupPosts = groupPosts;
		this.interests = interests;
		this.comments = comments;
		this.postings = postings;
		this.userPostings = userPostings;
		this.clock = clock;
	}

	/**
	 * 그룹에 공고를 올린다. 올린 공고의 id(postId)를 돌려준다.
	 * 같은 공고(공고키가 같은 공고)가 이 그룹에 이미 있으면 409 ALREADY_IN_GROUP 과 그 공고의 postId.
	 * 이 그룹에서 내렸던 공고면 같은 행을 되살린다. 다른 그룹에 있는 공고면 그 공고 본체를 함께 쓴다(설계 가정 2).
	 */
	@Transactional
	long post(long groupId, long userId, PostingDraft draft) {
		this.access.requireMember(groupId, userId);
		long postingId = this.postings.findOrCreate(draft, userId);
		Instant now = this.clock.instant();
		if (this.groupPosts.insertIfAbsent(groupId, postingId, userId, now) == 1
				|| this.groupPosts.reviveIfTakenDown(groupId, postingId, userId, now) == 1) {
			return postingId;
		}
		throw new ApiException(GroupPostErrorCode.ALREADY_IN_GROUP).with("postId", postingId);
	}

	/** 그룹에서 내린다. 올린 사람과 그룹장만 한다(아니면 403 FORBIDDEN). 행은 남고 목록에서 빠진다. */
	@Transactional
	void takeDown(long groupId, long postingId, long userId) {
		GroupMembership membership = this.access.requireMember(groupId, userId);
		GroupPost post = this.groupPosts.findById(new GroupPost.Key(groupId, postingId))
			.filter(GroupPost::isVisible)
			.orElseThrow(() -> new ApiException(GroupPostErrorCode.POST_NOT_FOUND));
		if (!membership.leader() && !post.isPostedBy(userId)) {
			throw new ApiException(CommonErrorCode.FORBIDDEN, "올린 사람과 그룹장만 내릴 수 있습니다");
		}
		post.takeDown(userId, this.clock.instant());
	}

	/** 날짜 넣기. 마감일을 읽지 못한 공고에만, 멤버 누구나, 한 번(설계 가정 5). 아니면 409 DEADLINE_ALREADY_SET. */
	@Transactional
	DeadlineResponse fillDeadline(long groupId, long postingId, long userId, LocalDate date) {
		requireVisiblePost(groupId, postingId, userId);
		if (!this.postings.fillUnknownDeadline(postingId, date, userId)) {
			throw new ApiException(GroupPostErrorCode.DEADLINE_ALREADY_SET);
		}
		return DeadlineResponse.of("DATE", date);
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

	/** 댓글 수정. 쓴 사람만 한다(그룹장도 못 한다). */
	@Transactional
	void editComment(long groupId, long postingId, long commentId, long userId, String text) {
		requireVisiblePost(groupId, postingId, userId);
		GroupPostComment comment = commentOf(groupId, postingId, commentId);
		if (!comment.isWrittenBy(userId)) {
			throw new ApiException(CommonErrorCode.FORBIDDEN, "쓴 사람만 고칠 수 있습니다");
		}
		comment.edit(text.strip(), this.clock.instant());
	}

	/** 댓글 삭제. 쓴 사람과 그룹장이 한다. */
	@Transactional
	void deleteComment(long groupId, long postingId, long commentId, long userId) {
		GroupMembership membership = requireVisiblePost(groupId, postingId, userId);
		GroupPostComment comment = commentOf(groupId, postingId, commentId);
		if (!comment.isWrittenBy(userId) && !membership.leader()) {
			throw new ApiException(CommonErrorCode.FORBIDDEN, "쓴 사람과 그룹장만 지울 수 있습니다");
		}
		this.comments.delete(comment);
	}

	private GroupMembership requireVisiblePost(long groupId, long postingId, long userId) {
		GroupMembership membership = this.access.requireMember(groupId, userId);
		if (!this.queries.isVisible(groupId, postingId)) {
			throw new ApiException(GroupPostErrorCode.POST_NOT_FOUND);
		}
		return membership;
	}

	private GroupPostComment commentOf(long groupId, long postingId, long commentId) {
		return this.comments.findById(commentId)
			.filter((comment) -> comment.belongsTo(groupId, postingId))
			.orElseThrow(() -> new ApiException(GroupPostErrorCode.COMMENT_NOT_FOUND));
	}

}
