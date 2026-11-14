package com.readme.group;

import java.time.Instant;
import java.util.List;

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

	/** 이 사람이 그룹장인 그룹들. */
	@Query(value = "select group_id from group_members where user_id = :userId and role = 'LEADER'", nativeQuery = true)
	List<Long> findLedGroupIds(@Param("userId") long userId);

	/** 멤버 하나를 뺀다. 바로 실행되어 같은 트랜잭션의 뒤 쿼리가 본다. 그 그룹의 그 사람 관심은 DB 가 함께 지운다. */
	@Modifying
	@Query(value = "delete from group_members where group_id = :groupId and user_id = :userId", nativeQuery = true)
	int deleteMember(@Param("groupId") long groupId, @Param("userId") long userId);

	/** 가장 먼저 들어온 멤버(들어온 시각이 같으면 id 가 작은 사람)를 그룹장으로 올린다. 멤버가 없으면 0. */
	@Modifying
	@Query(value = """
			update group_members set role = 'LEADER'
			 where group_id = :groupId
			   and user_id = (select user_id from group_members where group_id = :groupId
			                   order by joined_at, user_id limit 1)
			""", nativeQuery = true)
	int promoteEarliest(@Param("groupId") long groupId);

}
