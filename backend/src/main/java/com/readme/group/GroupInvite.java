package com.readme.group;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 초대 링크(group_invites). 토큰 원문은 링크에만 있고 여기에는 해시만 둔다. 만료 전까지 여러 사람이 쓸 수 있다.
 */
@Entity
@Table(name = "group_invites")
class GroupInvite {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long groupId;

	/** char(64). Hibernate 는 String 을 varchar 로 보므로 칸의 종류를 알려 준다 */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 64)
	private String tokenHash;

	private Long createdBy;

	private Instant expiresAt;

	private Instant revokedAt;

	private Instant createdAt;

	protected GroupInvite() {
	}

	GroupInvite(long groupId, String tokenHash, long createdBy, Instant expiresAt, Instant now) {
		this.groupId = groupId;
		this.tokenHash = tokenHash;
		this.createdBy = createdBy;
		this.expiresAt = expiresAt;
		this.createdAt = now;
	}

	long groupId() {
		return this.groupId;
	}

	/** 취소되지 않았고 만료 시각 전이다. */
	boolean usableAt(Instant now) {
		return this.revokedAt == null && now.isBefore(this.expiresAt);
	}

}
