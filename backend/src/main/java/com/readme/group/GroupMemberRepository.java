package com.readme.group;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMember.Key> {

	/** 멤버로 넣는다(공개 범위는 기본값). 이미 멤버면 그대로 둔다. 같은 초대를 두 번 눌러도 한 번만 들어간다. */
	@Modifying
	@Query(value = """
			insert into group_members (group_id, user_id, role, joined_at)
			values (:groupId, :userId, 'MEMBER', :joinedAt)
			on conflict do nothing
			""", nativeQuery = true)
	int insertMemberIfAbsent(@Param("groupId") long groupId, @Param("userId") long userId,
			@Param("joinedAt") Instant joinedAt);

}
