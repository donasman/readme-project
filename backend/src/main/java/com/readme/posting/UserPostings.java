package com.readme.posting;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 공고(user_postings)를 바꾸는 곳. 다른 기능은 이 클래스로만 내 공고 행을 만들고 고친다.
 */
@Service
public class UserPostings {

	private final UserPostingRepository repository;

	private final Clock clock;

	UserPostings(UserPostingRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/** 내 공고 목록에 이 공고를 넣는다. 이미 있으면 그대로 둔다(상태를 지우지 않는다). */
	@Transactional
	public void addIfAbsent(long userId, long postingId) {
		this.repository.insertIfAbsent(userId, postingId);
	}

	/** 내 지원 상태를 바꾼다. 내 공고 행이 없으면 만든다. 값의 짝은 부르는 쪽이 확인한다({@link ApplicationStatus#isConsistent}). */
	@Transactional
	void setStatus(long userId, long postingId, ApplicationStatus status) {
		this.repository.upsertStatus(userId, postingId, status.decision().name(),
				(status.stage() != null) ? status.stage().name() : null,
				(status.result() != null) ? status.result().name() : null, this.clock.instant());
	}

	/** 나만 보는 메모를 바꾼다. 빈 메모는 null 로 둔다. 내 공고에 없는 공고면 false. */
	@Transactional
	boolean setMemo(long userId, long postingId, String memo) {
		String stored = (memo == null || memo.isBlank()) ? null : memo;
		return this.repository.updateMemo(userId, postingId, stored, this.clock.instant()) == 1;
	}

}
