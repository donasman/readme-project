package com.readme.posting;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import com.readme.posting.ApplicationStatus.Decision;
import com.readme.posting.ApplicationStatus.Result;
import com.readme.posting.ApplicationStatus.Stage;

/**
 * 내 공고 한 줄(user_postings). 사용자 × 공고에 한 행이고, 지원 상태의 원본이다.
 */
@Entity
@Table(name = "user_postings")
@IdClass(UserPosting.Key.class)
class UserPosting {

	@Id
	private Long userId;

	@Id
	private Long postingId;

	@Enumerated(EnumType.STRING)
	private Decision decision;

	@Enumerated(EnumType.STRING)
	private Stage stage;

	@Enumerated(EnumType.STRING)
	private Result result;

	private Instant createdAt;

	private Instant updatedAt;

	protected UserPosting() {
	}

	record Key(Long userId, Long postingId) implements Serializable {
	}

}
