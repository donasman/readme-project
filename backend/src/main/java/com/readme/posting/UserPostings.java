package com.readme.posting;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.readme.common.error.ApiException;

/**
 * 내 공고(user_postings)를 바꾸는 곳. 다른 기능은 이 클래스로만 내 공고 행을 만들고 고친다.
 */
@Service
public class UserPostings {

	private final UserPostingRepository repository;

	private final Postings postings;

	private final Clock clock;

	UserPostings(UserPostingRepository repository, Postings postings, Clock clock) {
		this.repository = repository;
		this.postings = postings;
		this.clock = clock;
	}

	/**
	 * 그룹 없이 내 공고에 넣는다(설계 4.2). 같은 공고(공고키가 같은 공고)가 있으면 그 공고에 내 행을 만들고, 그 공고의 값은
	 * 고치지 않는다. 내 행은 관심을 켤 때처럼 미정으로 시작한다. 넣은 공고의 id 를 돌려준다.
	 * 이미 내 공고에 있으면 409 ALREADY_IN_MY_POSTINGS 와 그 공고의 postingId. 공고키가 있는 공고는 두 번 눌러도 행이 하나다.
	 * 키를 만들 수 없는 아주 긴 주소의 공고는 같은 공고로 알아보지 못하므로 넣을 때마다 새 공고와 새 행이 생긴다.
	 */
	@Transactional
	long add(long userId, PostingDraft draft) {
		long postingId = this.postings.findOrCreate(draft, userId);
		if (this.repository.insertIfAbsent(userId, postingId) == 0) {
			throw new ApiException(PostingErrorCode.ALREADY_IN_MY_POSTINGS).with("postingId", postingId);
		}
		return postingId;
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
