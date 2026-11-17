package com.readme.auth;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 계정(users). 만들기와 고치기는 경합이 있어서 {@link AccountRepository} 의 조건 붙은 native 쿼리로 한다.
 * 이 엔티티는 테이블과 맞는지 서버가 뜰 때 확인하는 데(ddl-auto: validate)와 리포지토리의 타입으로 쓴다.
 */
@Entity
@Table(name = "users")
class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String email;

	/** null 이면 아직 인증 전 */
	private Instant emailVerifiedAt;

	/** 구글로만 가입한 계정은 null */
	private String passwordHash;

	private String displayName;

	private Instant createdAt;

	private Instant updatedAt;

	private Instant lastLoginAt;

	protected Account() {
	}

}
