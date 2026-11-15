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

	/** 생년월일·성별을 통계와 생일 이벤트에 쓰는 데 동의한 때(V8). null 이면 동의하지 않았다 */
	private Instant birthGenderConsentAt;

	/** 이벤트·혜택 안내를 받는 데 동의한 때(V8). null 이면 동의하지 않았다 */
	private Instant eventNoticeConsentAt;

	protected Account() {
	}

}
