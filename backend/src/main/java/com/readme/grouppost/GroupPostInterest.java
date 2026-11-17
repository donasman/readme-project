package com.readme.grouppost;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 그룹 × 공고 × 사람의 관심 표시(group_post_interests). 행이 있으면 관심을 켠 것이다.
 */
@Entity
@Table(name = "group_post_interests")
@IdClass(GroupPostInterest.Key.class)
class GroupPostInterest {

	@Id
	private Long groupId;

	@Id
	private Long postingId;

	@Id
	private Long userId;

	private Instant createdAt;

	protected GroupPostInterest() {
	}

	record Key(Long groupId, Long postingId, Long userId) implements Serializable {
	}

}
