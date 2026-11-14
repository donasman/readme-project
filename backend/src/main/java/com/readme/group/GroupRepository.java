package com.readme.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GroupRepository extends JpaRepository<Group, Long> {

	/**
	 * 멤버가 없는 그룹을 지운다. 지웠으면 1. 그룹 공고·관심·댓글·초대는 DB 가 함께 지운다(공고 본체와 내 공고는 남는다).
	 * 멤버가 남아 있으면 지우지 않는다.
	 */
	@Modifying
	@Query(value = """
			delete from groups g
			 where g.id = :id and not exists (select 1 from group_members m where m.group_id = g.id)
			""", nativeQuery = true)
	int deleteIfEmpty(@Param("id") long id);

}
