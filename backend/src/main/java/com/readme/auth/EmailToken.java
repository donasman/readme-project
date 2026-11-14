package com.readme.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 가입 확인·비밀번호 재설정 메일의 토큰(email_tokens). 원문은 메일의 링크에만 있고 여기에는 해시만 둔다. 한 번만 쓴다.
 */
@Entity
@Table(name = "email_tokens")
class EmailToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	@Enumerated(EnumType.STRING)
	private Purpose purpose;

	/** char(64). Hibernate 는 String 을 varchar 로 보므로 칸의 종류를 알려 준다 */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 64)
	private String tokenHash;

	private Instant expiresAt;

	private Instant usedAt;

	private Instant createdAt;

	protected EmailToken() {
	}

	EmailToken(long userId, Purpose purpose, String tokenHash, Instant expiresAt, Instant now) {
		this.userId = userId;
		this.purpose = purpose;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = now;
	}

	long id() {
		return this.id;
	}

	long userId() {
		return this.userId;
	}

	enum Purpose {
		VERIFY_EMAIL, RESET_PASSWORD
	}

}
