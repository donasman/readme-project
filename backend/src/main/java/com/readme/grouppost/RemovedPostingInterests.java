package com.readme.grouppost;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.readme.posting.MyPostingRemoved;

/**
 * 내 공고에서 공고를 빼면(posting 의 {@link MyPostingRemoved}) 그 사람이 내 그룹들에서 켜 둔 그 공고의 관심을 끈다(공고 설계 4.2).
 * 뺀 쪽의 트랜잭션에서 바로 돈다. 그룹 공고와 댓글, 다른 멤버의 관심은 그대로다.
 */
@Component
class RemovedPostingInterests {

	private final GroupPostInterestRepository interests;

	RemovedPostingInterests(GroupPostInterestRepository interests) {
		this.interests = interests;
	}

	@EventListener
	void turnOff(MyPostingRemoved event) {
		this.interests.deleteEverywhere(event.postingId(), event.userId());
	}

}
