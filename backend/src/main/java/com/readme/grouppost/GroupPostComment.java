package com.readme.grouppost;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 그룹 × 공고에 달린 댓글(group_post_comments). 본문은 글자 그대로 두고, 링크는 응답을 만들 때 나눈다.
 */
@Entity
@Table(name = "group_post_comments")
class GroupPostComment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long groupId;

	private Long postingId;

	/** 쓴 사람. 계정을 지우면 null 이 된다 */
	private Long authorId;

	private String body;

	private Instant createdAt;

	private Instant updatedAt;

	protected GroupPostComment() {
	}

	GroupPostComment(long groupId, long postingId, long authorId, String body, Instant now) {
		this.groupId = groupId;
		this.postingId = postingId;
		this.authorId = authorId;
		this.body = body;
		this.createdAt = now;
		this.updatedAt = now;
	}

	Long getId() {
		return this.id;
	}

	/** 그 그룹의 그 공고에 달린 댓글인지. */
	boolean belongsTo(long groupId, long postingId) {
		return this.groupId == groupId && this.postingId == postingId;
	}

	boolean isWrittenBy(long userId) {
		return this.authorId != null && this.authorId == userId;
	}

	void edit(String body, Instant now) {
		this.body = body;
		this.updatedAt = now;
	}

}
