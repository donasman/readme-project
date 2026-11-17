package com.readme.group;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.readme.auth.AccountDeleting;

/**
 * 그룹장이 탈퇴할 때(2026-11-15 정함, 설계 9절): 그룹장인 그룹마다 나 말고 가장 먼저 들어온 멤버가 그룹장이 된다.
 * 혼자인 그룹은 지운다.
 *
 * <p>auth 가 계정을 지우기 바로 전에 내는 {@link AccountDeleting} 을 같은 트랜잭션에서 받는다. 여기서 실패하면 탈퇴도 취소된다.
 * 그룹장이 아닌 그룹의 멤버 행은 계정을 지울 때 DB 가 지운다. 탈퇴 화면의 미리 보기(auth 의 WithdrawalQueries)가 같은 순서로
 * 이어받을 사람을 보여 준다.
 */
@Component
class LeaderHandover {

	private final GroupMemberRepository members;

	private final GroupRepository groups;

	LeaderHandover(GroupMemberRepository members, GroupRepository groups) {
		this.members = members;
		this.groups = groups;
	}

	@EventListener
	void handOver(AccountDeleting event) {
		for (long groupId : this.members.findLedGroupIds(event.userId())) {
			// 그룹장은 그룹에 한 명뿐이라(부분 유일 인덱스) 나를 먼저 빼고 올린다
			this.members.deleteMember(groupId, event.userId());
			if (this.members.promoteEarliest(groupId) == 0) {
				// shortcut: 그 사이 마지막 멤버가 들어오거나 나가면 그룹장 없는 그룹이 남을 수 있다(지우지는 않는다),
				// 실제로 생기면 그룹 행을 잠그고 넘긴다
				this.groups.deleteIfEmpty(groupId);
			}
		}
	}

}
