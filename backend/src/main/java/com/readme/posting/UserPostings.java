package com.readme.posting;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 공고(user_postings)를 바꾸는 곳. 다른 기능은 이 클래스로만 내 공고 행을 만들고 고친다.
 */
@Service
public class UserPostings {

	private final UserPostingRepository repository;

	UserPostings(UserPostingRepository repository) {
		this.repository = repository;
	}

	/** 내 공고 목록에 이 공고를 넣는다. 이미 있으면 그대로 둔다(상태를 지우지 않는다). */
	@Transactional
	public void addIfAbsent(long userId, long postingId) {
		this.repository.insertIfAbsent(userId, postingId);
	}

}
