package com.readme.grouppost;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 그룹 × 공고(group_posts). 그룹에서 내려도 행은 남고 taken_down_at 만 채운다(설계 가정 4).
 */
@Entity
@Table(name = "group_posts")
@IdClass(GroupPost.Key.class)
class GroupPost {

	@Id
	private Long groupId;

	@Id
	private Long postingId;

	/** 이 그룹에 올린 사람. 계정을 지우면 null */
	private Long postedBy;

	private Instant postedAt;

	private Instant takenDownAt;

	private Long takenDownBy;

	protected GroupPost() {
	}

	boolean isVisible() {
		return this.takenDownAt == null;
	}

	boolean isPostedBy(long userId) {
		return Objects.equals(this.postedBy, userId);
	}

	void takeDown(long userId, Instant now) {
		this.takenDownAt = now;
		this.takenDownBy = userId;
	}

	record Key(Long groupId, Long postingId) implements Serializable {
	}

}
